package com.orderflow.product;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query(
            """
            SELECT p FROM Product p
            LEFT JOIN FETCH p.category
            WHERE (:status IS NULL OR p.status = :status)
              AND (:category IS NULL OR LOWER(p.category.name) = LOWER(:category))
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Product> search(
            @Param("status") ProductStatus status,
            @Param("category") String category,
            @Param("search") String search,
            Pageable pageable);
}
