package com.orderflow.inventory;

public record InventoryDto(
        Long productId, Long warehouseId, int availableQuantity, int reservedQuantity, Long version) {

    public static InventoryDto from(Inventory inventory) {
        return new InventoryDto(
                inventory.getProductId(),
                inventory.getWarehouseId(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity(),
                inventory.getVersion());
    }
}
