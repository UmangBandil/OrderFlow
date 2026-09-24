package com.orderflow.order;

import com.orderflow.audit.AuditService;
import com.orderflow.cart.Cart;
import com.orderflow.cart.CartItem;
import com.orderflow.cart.CartRepository;
import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import com.orderflow.common.error.ResourceNotFoundException;
import com.orderflow.common.events.EventPublisher;
import com.orderflow.idempotency.IdempotencyService;
import com.orderflow.inventory.InventoryService;
import com.orderflow.order.OrderDtos.CreateOrderRequest;
import com.orderflow.order.OrderDtos.OrderDto;
import com.orderflow.payment.Payment;
import com.orderflow.payment.PaymentService;
import com.orderflow.payment.PaymentStatus;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import com.orderflow.product.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Order saga orchestrator. Each phase commits independently:
 *
 * <pre>
 * [tx1] create order (PENDING_PAYMENT)        -> durable even if later steps fail
 * [tx2] reserve inventory (optimistic retry)  -> failure cancels order (tx3)
 * [tx3] mark PAID/CONFIRMED or cancel + release
 * </pre>
 *
 * Payment failures therefore leave a durable, auditable CANCELLED order and publish
 * order.cancelled via the outbox, instead of erasing the whole attempt.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter ORDER_NUMBER_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final EventPublisher eventPublisher;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final OrderMetrics orderMetrics;
    private final TransactionTemplate transactionTemplate;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            ProductRepository productRepository,
            InventoryService inventoryService,
            PaymentService paymentService,
            EventPublisher eventPublisher,
            IdempotencyService idempotencyService,
            AuditService auditService,
            OrderMetrics orderMetrics,
            TransactionTemplate transactionTemplate) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.orderMetrics = orderMetrics;
        this.transactionTemplate = transactionTemplate;
    }

    // ------------------------------------------------------------------
    // Query
    // ------------------------------------------------------------------

    public Page<OrderDto> search(
            OrderStatus status,
            Long customerId,
            LocalDate from,
            LocalDate to,
            String paymentStatus,
            Long warehouseId,
            Pageable pageable) {
        Specification<Order> spec =
                OrderSpecifications.withFilters(status, customerId, from, to, paymentStatus, warehouseId);
        return orderRepository.findAll(spec, pageable).map(OrderDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        return OrderDto.from(requireOrder(id));
    }

    /** Visibility rule: staff sees everything, customers only their own orders. */
    @Transactional(readOnly = true)
    public OrderDto getForUser(Long id, Long userId, boolean isStaff) {
        Order order = requireOrder(id);
        if (!isStaff && !order.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only view your own orders");
        }
        return OrderDto.from(order);
    }

    @Transactional(readOnly = true)
    public OrderDto getByOrderNumber(String orderNumber) {
        return OrderDto.from(orderRepository
                .findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException(orderNumber)));
    }

    // ------------------------------------------------------------------
    // Saga: create
    // ------------------------------------------------------------------

    public OrderDto createFromCart(Long userId, String idempotencyKey, CreateOrderRequest request) {
        Optional<OrderDto> replayed =
                idempotencyService.findResponse(idempotencyKey, userId, "/api/v1/orders", OrderDto.class);
        if (replayed.isPresent()) {
            log.info(
                    "Idempotent replay for key {} -> order {}",
                    idempotencyKey,
                    replayed.get().orderNumber());
            return replayed.get();
        }

        // Phase 1: validate + persist order in its own transaction
        SagaOrder saga = transactionTemplate.execute(tx -> prepareOrder(userId, request));

        // Phase 2: reserve inventory (separate transaction, optimistic-lock retries)
        try {
            inventoryService.reserveForOrder(saga.order().getId(), saga.productQuantities());
        } catch (ApiException e) {
            cancelOrder(saga.order(), "Inventory reservation failed", "INVENTORY_FAILED");
            throw e;
        }
        recordOrderCreated(saga);

        // Phase 3: payment; on failure compensate and surface 402
        try {
            Payment payment = paymentService.processForOrder(saga.order(), saga.total());
            if (payment.getStatus() != PaymentStatus.SUCCESS) {
                throw new ApiException(ErrorCode.PAYMENT_FAILED, "Payment declined");
            }
            confirmOrder(saga, payment);
        } catch (ApiException e) {
            eventPublisher.publish(
                    "payment.failed",
                    "payment",
                    saga.order().getOrderNumber(),
                    Map.of(
                            "orderId",
                            saga.order().getId(),
                            "reason",
                            e.getMessage() == null ? "PAYMENT_FAILED" : e.getMessage()));
            releaseInventory(saga);
            cancelOrder(saga.order(), "Payment failed: " + e.getMessage(), "PAYMENT_FAILED");
            throw e;
        }

        // Phase 4: finalize (idempotency record + cart clear + audit) in its own transaction
        OrderDto dto = transactionTemplate.execute(tx -> {
            Order order = requireOrder(saga.order().getId());
            if (saga.cart() != null) {
                saga.cart().getItems().clear();
            }
            idempotencyService.storeResponse(idempotencyKey, userId, "/api/v1/orders", OrderDto.from(order));
            auditService.record(
                    userId, "CREATED_ORDER", "order", String.valueOf(order.getId()), null, order.getOrderNumber());
            return OrderDto.from(order);
        });
        return dto;
    }

    // ------------------------------------------------------------------
    // Saga phases
    // ------------------------------------------------------------------

    private SagaOrder prepareOrder(Long userId, CreateOrderRequest request) {
        List<CartItem> sourceItems = new java.util.ArrayList<>();
        boolean fromRequest = request.items() != null && !request.items().isEmpty();
        Cart cart = null;
        if (fromRequest) {
            for (OrderDtos.CartItemRequest cir : request.items()) {
                sourceItems.add(new CartItem(cir.productId(), cir.quantity()));
            }
        } else {
            cart = cartRepository
                    .findByUserId(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Cart is empty"));
            if (cart.getItems().isEmpty()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Cart is empty");
            }
            sourceItems.addAll(cart.getItems());
        }

        String addressText = request.shippingAddress() == null
                ? ""
                : String.format(
                        "%s, %s %s%s",
                        request.shippingAddress().addressLine(),
                        request.shippingAddress().city(),
                        request.shippingAddress().postalCode(),
                        request.shippingAddress().country() == null
                                ? ""
                                : ", " + request.shippingAddress().country());

        Order order = new Order(generateOrderNumber(), userId, BigDecimal.ZERO, addressText);
        Map<Long, Integer> productQuantities = new LinkedHashMap<>();
        for (CartItem item : sourceItems) {
            Product product = productRepository
                    .findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", item.getProductId()));
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Product no longer available: " + product.getSku());
            }
            order.addItem(new OrderItem(product.getId(), item.getQuantity(), product.getPrice()));
            productQuantities.merge(product.getId(), item.getQuantity(), Integer::sum);
        }
        order.recalculateTotal();
        order.transitionTo(OrderStatus.PENDING_PAYMENT);
        Order saved = orderRepository.save(order);
        return new SagaOrder(saved, productQuantities, saved.getTotalAmount(), cart);
    }

    private void recordOrderCreated(SagaOrder saga) {
        eventPublisher.publish(
                "order.created",
                "order",
                saga.order().getOrderNumber(),
                Map.of("orderId", saga.order().getId(), "total", saga.total()));
        orderMetrics.orderCreated();
    }

    private void confirmOrder(SagaOrder saga, Payment payment) {
        transactionTemplate.executeWithoutResult(tx -> {
            Order order = requireOrder(saga.order().getId());
            order.transitionTo(OrderStatus.PAID);
            order.transitionTo(OrderStatus.CONFIRMED);
            eventPublisher.publish(
                    "payment.completed", "payment", String.valueOf(payment.getId()), Map.of("orderId", order.getId()));
            eventPublisher.publish(
                    "order.confirmed", "order", order.getOrderNumber(), Map.of("orderId", order.getId()));
        });
    }

    private void cancelOrder(Order order, String reason, String eventReason) {
        transactionTemplate.executeWithoutResult(tx -> {
            Order managed = requireOrder(order.getId());
            managed.cancel(reason);
            eventPublisher.publish("order.cancelled", "order", managed.getOrderNumber(), Map.of("reason", eventReason));
            orderMetrics.orderCancelled();
        });
        log.warn("Order {} cancelled: {}", order.getOrderNumber(), reason);
    }

    private void releaseInventory(SagaOrder saga) {
        try {
            inventoryService.releaseForOrder(saga.order().getId(), saga.productQuantities());
        } catch (Exception e) {
            // Compensation is best-effort; never mask the original failure
            log.error("Inventory release failed for order {}: {}", saga.order().getOrderNumber(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Saga: cancel (user/staff initiated)
    // ------------------------------------------------------------------

    @Transactional
    public OrderDto cancel(Long id, Long userId, boolean isStaff, String reason) {
        Order order = requireOrder(id);
        if (!isStaff && !order.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only cancel your own orders");
        }
        if (!order.isCancellableByCustomer()) {
            throw new OrderCancellationException("Order in status " + order.getStatus() + " cannot be cancelled");
        }
        Map<Long, Integer> productQuantities = new LinkedHashMap<>();
        order.getItems()
                .forEach(item -> productQuantities.merge(item.getProductId(), item.getQuantity(), Integer::sum));
        try {
            inventoryService.releaseForOrder(order.getId(), productQuantities);
        } catch (Exception e) {
            log.warn(
                    "Inventory release during cancellation failed for order {}: {}",
                    order.getOrderNumber(),
                    e.getMessage());
        }
        order.cancel(reason == null ? "Cancelled by user" : reason);
        eventPublisher.publish(
                "order.cancelled",
                "order",
                order.getOrderNumber(),
                Map.of("reason", reason == null ? "CANCELLED" : reason));
        orderMetrics.orderCancelled();
        auditService.record(
                userId,
                "CANCELLED_ORDER",
                "order",
                String.valueOf(order.getId()),
                order.getStatus().name(),
                "CANCELLED");
        return OrderDto.from(order);
    }

    private Order requireOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private String generateOrderNumber() {
        return "ORD-" + LocalDateTime.now().format(ORDER_NUMBER_FMT) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** Carries saga state between phases. */
    private record SagaOrder(Order order, Map<Long, Integer> productQuantities, BigDecimal total, Cart cart) {}
}
