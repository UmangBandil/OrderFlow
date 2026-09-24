package com.orderflow.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<Inventory> findAllByProductIdIn(Collection<Long> productIds);

    @Query(
            """
            SELECT COALESCE(SUM(i.reservedQuantity), 0)
            FROM Inventory i
            WHERE i.productId IN :productIds
            """)
    long sumReservedQuantity(@Param("productIds") Collection<Long> productIds);
}
