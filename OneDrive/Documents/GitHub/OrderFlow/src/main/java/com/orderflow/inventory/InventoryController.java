package com.orderflow.inventory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_MANAGER')")
@Tag(name = "Inventory", description = "Stock reservation, release and adjustment (ADMIN / WAREHOUSE_MANAGER)")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @Operation(summary = "List all inventory rows")
    public List<InventoryDto> list() {
        return inventoryService.listAll();
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get inventory for a product")
    public InventoryDto get(@PathVariable Long productId) {
        return inventoryService.getByProduct(productId);
    }

    @PostMapping("/reserve")
    @Operation(summary = "Reserve stock (manual, warehouse operations)")
    public ResponseEntity<InventoryDto> reserve(@Valid @RequestBody ReserveRequest request) {
        InventoryDto dto = inventoryService.adjustStock(request.productId(), -request.quantity());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/release")
    @Operation(summary = "Release reserved stock back to available")
    public ResponseEntity<InventoryDto> release(@Valid @RequestBody ReserveRequest request) {
        InventoryDto dto = inventoryService.adjustStock(request.productId(), request.quantity());
        return ResponseEntity.ok(dto);
    }

    public record ReserveRequest(@NotNull Long productId, @Min(1) int quantity) {}
}
