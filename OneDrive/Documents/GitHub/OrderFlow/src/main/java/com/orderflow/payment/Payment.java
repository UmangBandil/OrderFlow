package com.orderflow.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "provider_ref", nullable = false, unique = true, length = 64)
    private String providerRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Payment() {}

    public Payment(Long orderId, BigDecimal amount, String providerRef) {
        this.orderId = orderId;
        this.amount = amount;
        this.providerRef = providerRef;
        this.status = PaymentStatus.PENDING;
    }

    public void markSuccess() {
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
