package com.orderflow.product;

import com.orderflow.product.ProductDtos.CreateProductRequest;
import com.orderflow.product.ProductDtos.ProductDto;
import com.orderflow.product.ProductDtos.UpdateProductRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product catalog with pagination, filtering, sorting and search")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List products with pagination, filtering and search")
    public Page<ProductDto> list(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return productService.search(status, category, search, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    public ProductDto get(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a product (ADMIN)")
    public ResponseEntity<ProductDto> create(@Valid @RequestBody CreateProductHttpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.create(new CreateProductRequest(
                        request.sku(),
                        request.name(),
                        request.description(),
                        request.price(),
                        request.category(),
                        request.status())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a product (ADMIN)")
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody UpdateProductHttpRequest request) {
        return productService.update(
                id,
                new UpdateProductRequest(
                        request.name(), request.description(), request.price(), request.category(), request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Discontinue a product (ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateProductHttpRequest(
            @NotBlank String sku,
            @NotBlank String name,
            String description,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            String category,
            ProductStatus status) {}

    public record UpdateProductHttpRequest(
            @NotBlank String name,
            String description,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            String category,
            ProductStatus status) {}
}
