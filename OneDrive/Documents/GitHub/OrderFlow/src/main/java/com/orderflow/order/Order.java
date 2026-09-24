package com.orderflow.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 32)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "shipping_address", nullable = false, length = 500)
    private String shippingAddress;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    protected Order() {}

    public Order(String orderNumber, Long userId, BigDecimal totalAmount, String shippingAddress) {
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.shippingAddress = shippingAddress;
        this.status = OrderStatus.CREATED;
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    /** Recomputes totalAmount from the current items. */
    public void recalculateTotal() {
        this.totalAmount = items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Valid transitions from CREATED/PENDING_PAYMENT through the happy path. */
    public void transitionTo(OrderStatus newStatus) {
        this.status = newStatus;
    }

    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
    }

    public boolean isCancellableByCustomer() {
        return switch (status) {
            case CREATED, PENDING_PAYMENT, PAID, CONFIRMED -> true;
            default -> false;
        };
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
