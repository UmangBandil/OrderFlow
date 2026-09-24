package com.orderflow.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "inventory")
@EntityListeners(AuditingEntityListener.class)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", updatable = false, insertable = false)
    private java.time.OffsetDateTime createdAt;

    protected Inventory() {}

    public Inventory(Long productId, Long warehouseId, int availableQuantity) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = 0;
    }

    /** Reserves quantity; throws if it would drive available stock negative. */
    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }
        if (availableQuantity < quantity) {
            throw new com.orderflow.common.error.ApiException(
                    com.orderflow.common.error.ErrorCode.INSUFFICIENT_INVENTORY, "Requested quantity is unavailable");
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    /** Releases a reservation back to available stock. */
    public void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be positive");
        }
        if (reservedQuantity < quantity) {
            throw new IllegalStateException("Cannot release more than reserved");
        }
        reservedQuantity -= quantity;
        availableQuantity += quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    /** Increases available stock (e.g. restock). */
    public void increaseAvailable(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        availableQuantity += quantity;
    }

    /** Decreases available stock (e.g. shrinkage); refuses to go negative. */
    public void reduceAvailable(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (availableQuantity - quantity < 0) {
            throw new com.orderflow.common.error.ApiException(
                    com.orderflow.common.error.ErrorCode.INSUFFICIENT_INVENTORY, "Stock cannot go negative");
        }
        availableQuantity -= quantity;
    }

    public Long getVersion() {
        return version;
    }
}
