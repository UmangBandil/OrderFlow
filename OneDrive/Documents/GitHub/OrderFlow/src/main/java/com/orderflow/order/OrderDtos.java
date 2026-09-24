package com.orderflow.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {}

    public record ShippingAddress(
            @NotBlank String addressLine, @NotBlank String city, @NotBlank String postalCode, String country) {}

    public record CreateOrderRequest(
            @NotEmpty @Valid List<CartItemRequest> items, @Valid ShippingAddress shippingAddress) {}

    public record CartItemRequest(Long productId, int quantity) {}

    public record OrderItemDto(Long productId, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

        public static OrderItemDto from(OrderItem item) {
            return new OrderItemDto(item.getProductId(), item.getQuantity(), item.getUnitPrice(), item.lineTotal());
        }
    }

    public record OrderDto(
            Long id,
            String orderNumber,
            Long userId,
            String status,
            BigDecimal totalAmount,
            String shippingAddress,
            String cancelReason,
            List<OrderItemDto> items,
            OffsetDateTime createdAt) {

        public static OrderDto from(Order order) {
            return new OrderDto(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getUserId(),
                    order.getStatus().name(),
                    order.getTotalAmount(),
                    order.getShippingAddress(),
                    order.getCancelReason(),
                    order.getItems().stream().map(OrderItemDto::from).toList(),
                    order.getCreatedAt());
        }
    }
}
