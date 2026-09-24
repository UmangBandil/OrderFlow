package com.orderflow.order;

import com.orderflow.common.security.AuthFacade;
import com.orderflow.idempotency.IdempotencyService;
import com.orderflow.order.OrderDtos.CreateOrderRequest;
import com.orderflow.order.OrderDtos.OrderDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order creation with saga workflow, search and cancellation")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    public OrderController(OrderService orderService, IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    @Operation(summary = "Create an order from the request items (saga: reserve -> pay -> confirm)")
    public ResponseEntity<OrderDto> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        Long userId = AuthFacade.currentUserId();
        OrderDto dto = orderService.createFromCart(userId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    @Operation(summary = "Search orders with filters (customers see only their own)")
    public Page<OrderDto> search(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long customer,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) Long warehouse,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = AuthFacade.currentUserId();
        boolean isStaff = AuthFacade.currentUser().isAdmin();
        Long effectiveCustomer = isStaff ? customer : userId;
        return orderService.search(status, effectiveCustomer, from, to, paymentStatus, warehouse, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details (customers can only view their own orders)")
    public OrderDto get(@PathVariable Long id) {
        Long userId = AuthFacade.currentUserId();
        boolean isStaff = AuthFacade.currentUser().isAdmin()
                || AuthFacade.currentUser().isSupportAgent()
                || "WAREHOUSE_MANAGER".equals(AuthFacade.currentUser().getRole());
        return orderService.getForUser(id, userId, isStaff);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order (customers can cancel eligible orders)")
    public ResponseEntity<OrderDto> cancel(
            @PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        Long userId = AuthFacade.currentUserId();
        boolean isStaff =
                AuthFacade.currentUser().isAdmin() || AuthFacade.currentUser().isSupportAgent();
        return ResponseEntity.ok(orderService.cancel(id, userId, isStaff, request == null ? null : request.reason()));
    }

    public record CancelRequest(String reason) {}
}
