package com.orderflow.shipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(nullable = false, length = 64)
    private String carrier;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 64)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShipmentStatus status;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Shipment() {}

    public Shipment(Long orderId, String carrier, String trackingNumber) {
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.status = ShipmentStatus.CREATED;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    /** Allowed progression for shipment lifecycle. */
    public void transitionTo(ShipmentStatus newStatus) {
        if (!isValidTransition(newStatus)) {
            throw new IllegalStateException("Illegal shipment transition " + status + " -> " + newStatus);
        }
        this.status = newStatus;
        if (newStatus == ShipmentStatus.SHIPPED) {
            this.shippedAt = OffsetDateTime.now();
        }
        if (newStatus == ShipmentStatus.DELIVERED) {
            this.deliveredAt = OffsetDateTime.now();
        }
    }

    private boolean isValidTransition(ShipmentStatus newStatus) {
        return switch (status) {
            case CREATED -> newStatus == ShipmentStatus.PACKED || newStatus == ShipmentStatus.FAILED;
            case PACKED -> newStatus == ShipmentStatus.SHIPPED || newStatus == ShipmentStatus.FAILED;
            case SHIPPED -> newStatus == ShipmentStatus.IN_TRANSIT || newStatus == ShipmentStatus.FAILED;
            case IN_TRANSIT -> newStatus == ShipmentStatus.OUT_FOR_DELIVERY || newStatus == ShipmentStatus.FAILED;
            case OUT_FOR_DELIVERY -> newStatus == ShipmentStatus.DELIVERED || newStatus == ShipmentStatus.FAILED;
            case DELIVERED, FAILED -> false;
        };
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getShippedAt() {
        return shippedAt;
    }

    public OffsetDateTime getDeliveredAt() {
        return deliveredAt;
    }
}
