package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.audit.AuditService;
import com.orderflow.cart.Cart;
import com.orderflow.cart.CartItem;
import com.orderflow.cart.CartRepository;
import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import com.orderflow.common.events.EventPublisher;
import com.orderflow.idempotency.IdempotencyService;
import com.orderflow.inventory.InventoryService;
import com.orderflow.order.OrderDtos.CreateOrderRequest;
import com.orderflow.order.OrderDtos.OrderDto;
import com.orderflow.payment.Payment;
import com.orderflow.payment.PaymentService;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private CartRepository cartRepository;
    private ProductRepository productRepository;
    private InventoryService inventoryService;
    private PaymentService paymentService;
    private EventPublisher eventPublisher;
    private IdempotencyService idempotencyService;
    private AuditService auditService;
    private OrderMetrics orderMetrics;
    private OrderService orderService;

    private Product product;
    private Cart cart;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        cartRepository = mock(CartRepository.class);
        productRepository = mock(ProductRepository.class);
        inventoryService = mock(InventoryService.class);
        paymentService = mock(PaymentService.class);
        eventPublisher = mock(EventPublisher.class);
        idempotencyService = mock(IdempotencyService.class);
        auditService = mock(AuditService.class);
        orderMetrics = new OrderMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        // Plain unit tests have no JPA infrastructure: use a no-op tx manager so the
        // TransactionTemplate phases execute their callbacks without a real database.
        org.springframework.transaction.PlatformTransactionManager noOpTx =
                new org.springframework.transaction.PlatformTransactionManager() {
                    @Override
                    public org.springframework.transaction.TransactionStatus getTransaction(
                            org.springframework.transaction.TransactionDefinition definition) {
                        return new org.springframework.transaction.support.SimpleTransactionStatus();
                    }

                    @Override
                    public void commit(org.springframework.transaction.TransactionStatus status) {}

                    @Override
                    public void rollback(org.springframework.transaction.TransactionStatus status) {}
                };
        orderService = new OrderService(
                orderRepository,
                cartRepository,
                productRepository,
                inventoryService,
                paymentService,
                eventPublisher,
                idempotencyService,
                auditService,
                orderMetrics,
                new org.springframework.transaction.support.TransactionTemplate(noOpTx));

        product = new Product("SKU-1", "Test Product", "desc", new BigDecimal("50.00"), null);
        cart = new Cart(7L);
        cart.addItem(new CartItem(101L, 2));

        when(idempotencyService.findResponse(any(), anyLong(), anyString(), eq(OrderDto.class)))
                .thenReturn(Optional.empty());
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(101L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                org.springframework.test.util.ReflectionTestUtils.setField(o, "id", 999L);
            }
            lastSaved = o;
            return o;
        });
        when(paymentService.processForOrder(any(Order.class), any(BigDecimal.class)))
                .thenAnswer(inv -> {
                    Payment p = new Payment(999L, new BigDecimal("100.00"), "SIM-x");
                    p.markSuccess();
                    return p;
                });
        // confirm/cancel phases run in their own transaction and re-load the order by id
        when(orderRepository.findById(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> java.util.Optional.ofNullable(lastSaved));
    }

    private Order lastSaved;

    private CreateOrderRequest request() {
        return new CreateOrderRequest(null, new OrderDtos.ShippingAddress("Street 1", "Pune", "411001", null));
    }

    @Test
    @DisplayName("happy path: order confirmed with correct total, cart cleared, events published")
    void happyPath() {
        OrderDto dto = orderService.createFromCart(7L, "key-1", request());

        assertThat(dto.status()).isEqualTo("CONFIRMED");
        assertThat(dto.totalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        verify(inventoryService).reserveForOrder(eq(999L), anyMap());
        verify(paymentService).processForOrder(any(Order.class), eq(new BigDecimal("100.00")));
        verify(eventPublisher).publish(eq("order.confirmed"), eq("order"), anyString(), any());
        verify(eventPublisher).publish(eq("order.created"), eq("order"), anyString(), any());
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("payment failure releases inventory and cancels order (saga compensation)")
    void paymentFailureCompensates() {
        when(paymentService.processForOrder(any(Order.class), any(BigDecimal.class)))
                .thenThrow(new ApiException(ErrorCode.PAYMENT_FAILED, "declined"));

        assertThatThrownBy(() -> orderService.createFromCart(7L, "key-2", request()))
                .isInstanceOf(ApiException.class);

        verify(inventoryService).releaseForOrder(anyLong(), anyMap());
        verify(eventPublisher).publish(eq("order.cancelled"), eq("order"), anyString(), any());
        verify(eventPublisher).publish(eq("payment.failed"), eq("payment"), anyString(), any());
    }

    @Test
    @DisplayName("inventory failure cancels the order and no payment is attempted")
    void inventoryFailureCancels() {
        doThrow(new ApiException(ErrorCode.INSUFFICIENT_INVENTORY, "unavailable"))
                .when(inventoryService)
                .reserveForOrder(anyLong(), anyMap());

        assertThatThrownBy(() -> orderService.createFromCart(7L, "key-3", request()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY);

        verify(paymentService, never()).processForOrder(any(Order.class), any());
        verify(eventPublisher).publish(eq("order.cancelled"), eq("order"), anyString(), any());
    }

    @Test
    @DisplayName("idempotency replay returns the stored order without creating a new one")
    void idempotentReplay() {
        OrderDto stored = new OrderDto(1L, "ORD-X", 7L, "CONFIRMED", BigDecimal.TEN, "addr", null, List.of(), null);
        when(idempotencyService.findResponse(eq("key-4"), eq(7L), anyString(), eq(OrderDto.class)))
                .thenReturn(Optional.of(stored));

        OrderDto dto = orderService.createFromCart(7L, "key-4", request());

        assertThat(dto.orderNumber()).isEqualTo("ORD-X");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("empty cart is rejected")
    void emptyCartRejected() {
        cart.getItems().clear();
        assertThatThrownBy(() -> orderService.createFromCart(7L, "key-5", request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("inactive product in cart is rejected")
    void inactiveProductRejected() {
        product.setStatus(com.orderflow.product.ProductStatus.DISCONTINUED);
        assertThatThrownBy(() -> orderService.createFromCart(7L, "key-6", request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    @DisplayName("cancel by owner releases inventory and publishes event")
    void cancelReleasesInventory() {
        Order order = new Order("ORD-C1", 7L, new BigDecimal("100.00"), "addr");
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", 5L);
        order.addItem(new OrderItem(101L, 2, new BigDecimal("50.00")));
        order.transitionTo(OrderStatus.PAID);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderDto dto = orderService.cancel(5L, 7L, false, "changed my mind");

        assertThat(dto.status()).isEqualTo("CANCELLED");
        verify(inventoryService).releaseForOrder(eq(5L), anyMap());
        verify(eventPublisher).publish(eq("order.cancelled"), eq("order"), eq("ORD-C1"), any());
    }

    @Test
    @DisplayName("customer cannot cancel someone else's order")
    void cancelOwnershipEnforced() {
        Order order = new Order("ORD-C2", 8L, new BigDecimal("100.00"), "addr");
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", 6L);
        order.transitionTo(OrderStatus.PAID);
        when(orderRepository.findById(6L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(6L, 7L, false, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("shipped order cannot be cancelled")
    void shippedOrderNotCancellable() {
        Order order = new Order("ORD-C3", 7L, new BigDecimal("100.00"), "addr");
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", 7L);
        order.transitionTo(OrderStatus.SHIPPED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(7L, 7L, true, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE);
    }
}
