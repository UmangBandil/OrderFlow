package com.orderflow.order;

public enum OrderStatus {
    CREATED,
    PENDING_PAYMENT,
    PAID,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    PAYMENT_FAILED,
    CANCELLED,
    REFUNDED
}
