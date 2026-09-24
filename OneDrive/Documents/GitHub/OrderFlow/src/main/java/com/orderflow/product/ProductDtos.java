package com.orderflow.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class ProductDtos {

    private ProductDtos() {}

    public record ProductDto(
            Long id,
            String sku,
            String name,
            String description,
            BigDecimal price,
            String category,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static ProductDto from(Product product) {
            return new ProductDto(
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    product.getCategory() != null ? product.getCategory().getName() : null,
                    product.getStatus().name(),
                    product.getCreatedAt(),
                    product.getUpdatedAt());
        }
    }

    public record CreateProductRequest(
            String sku, String name, String description, BigDecimal price, String category, ProductStatus status) {}

    public record UpdateProductRequest(
            String name, String description, BigDecimal price, String category, ProductStatus status) {}
}
