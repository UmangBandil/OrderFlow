package com.orderflow.inventory;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import com.orderflow.common.error.ResourceNotFoundException;
import com.orderflow.common.events.EventPublisher;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final int MAX_RETRIES = 3;

    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final EventPublisher eventPublisher;
    private final InventoryMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    public InventoryService(
            InventoryRepository inventoryRepository,
            WarehouseRepository warehouseRepository,
            EventPublisher eventPublisher,
            InventoryMetrics metrics,
            TransactionTemplate transactionTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
    }

    /** Reserves stock for an order. Retries on optimistic-lock contention. */
    public void reserveForOrder(Long orderId, Map<Long, Integer> productQuantities) {
        executeWithRetry(
                () -> transactionTemplate.executeWithoutResult(tx -> doReserve(orderId, productQuantities)),
                orderId,
                "reserve");
    }

    /** Releases stock for a failed/cancelled order (saga compensation). */
    public void releaseForOrder(Long orderId, Map<Long, Integer> productQuantities) {
        executeWithRetry(
                () -> transactionTemplate.executeWithoutResult(tx -> doRelease(orderId, productQuantities)),
                orderId,
                "release");
    }

    /** Warehouse/admin stock adjustment. Never allows negative stock. */
    public InventoryDto adjustStock(Long productId, int newAvailableQuantity) {
        return transactionTemplate.execute(tx -> {
            Inventory inventory = requireInventory(productId);
            if (newAvailableQuantity < 0) {
                throw new ApiException(ErrorCode.INSUFFICIENT_INVENTORY, "Stock cannot go negative");
            }
            int delta = newAvailableQuantity - inventory.getAvailableQuantity();
            if (delta >= 0) {
                inventory.increaseAvailable(delta);
            } else {
                inventory.reduceAvailable(-delta);
            }
            return InventoryDto.from(inventory);
        });
    }

    public List<InventoryDto> listAll() {
        return inventoryRepository.findAll().stream().map(InventoryDto::from).toList();
    }

    public InventoryDto getByProduct(Long productId) {
        return InventoryDto.from(requireInventory(productId));
    }

    private void executeWithRetry(Runnable action, Long orderId, String operation) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                action.run();
                if ("reserve".equals(operation)) {
                    metrics.reservationSucceeded();
                }
                return;
            } catch (OptimisticLockingFailureException e) {
                log.warn(
                        "Inventory {} contention on order {}, attempt {}/{}", operation, orderId, attempt, MAX_RETRIES);
                if (attempt == MAX_RETRIES) {
                    if ("reserve".equals(operation)) {
                        metrics.reservationFailed();
                    }
                    throw new ApiException(ErrorCode.CONFLICT, "Inventory is busy, please retry");
                }
            }
        }
    }

    private void doReserve(Long orderId, Map<Long, Integer> productQuantities) {
        productQuantities.forEach((productId, quantity) -> {
            Inventory inventory = requireInventory(productId);
            inventory.reserve(quantity);
            eventPublisher.publish(
                    "inventory.reserved",
                    "order",
                    String.valueOf(orderId),
                    Map.of("orderId", orderId, "productId", productId, "quantity", quantity));
        });
    }

    private void doRelease(Long orderId, Map<Long, Integer> productQuantities) {
        productQuantities.forEach((productId, quantity) -> {
            Inventory inventory = requireInventory(productId);
            inventory.release(quantity);
            eventPublisher.publish(
                    "inventory.released",
                    "order",
                    String.valueOf(orderId),
                    Map.of("orderId", orderId, "productId", productId, "quantity", quantity));
        });
    }

    private Inventory requireInventory(Long productId) {
        return inventoryRepository
                .findByProductIdAndWarehouseId(productId, defaultWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId));
    }

    /** Single-warehouse deployment: resolve the default warehouse dynamically. */
    private Long defaultWarehouseId() {
        return warehouseRepository
                .findFirstByOrderByIdAsc()
                .map(Warehouse::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "default"));
    }
}
