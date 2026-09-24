package com.orderflow.inventory;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByName(String name);

    /** The default (first) warehouse for single-warehouse deployments. */
    Optional<Warehouse> findFirstByOrderByIdAsc();
}
