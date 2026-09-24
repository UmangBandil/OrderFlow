package com.orderflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import com.orderflow.common.events.EventPublisher;
import com.orderflow.product.Product;
import com.orderflow.product.ProductRepository;
import com.orderflow.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration tests against a real PostgreSQL container (Flyway migrations run for real).
 * Redis/Kafka are not needed for these paths; the event publisher is mocked, cache disabled.
 */
@SpringBootTest
@ActiveProfiles("itest")
@Tag("integration")
class InventoryConcurrencyIT extends PostgresContainerSupport {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private com.orderflow.inventory.WarehouseRepository warehouseRepository;

    @MockBean
    private EventPublisher eventPublisher;

    private Long warehouseId;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PostgresContainerSupport.postgres()
                .getJdbcUrl());
        registry.add("spring.datasource.username", () -> PostgresContainerSupport.postgres()
                .getUsername());
        registry.add("spring.datasource.password", () -> PostgresContainerSupport.postgres()
                .getPassword());
        registry.add("spring.cache.type", () -> "none");
    }

    private Long productId;

    @BeforeEach
    void seed() {
        inventoryRepository.deleteAll();
        // Real product + warehouse rows so FK constraints hold
        var warehouse = warehouseRepository
                .findFirstByOrderByIdAsc()
                .orElseGet(() -> warehouseRepository.save(new com.orderflow.inventory.Warehouse("IT-DC", "Pune")));
        warehouseId = warehouse.getId();
        Product product = new Product(
                "IT-CONC-" + System.nanoTime(),
                "Concurrency Test Product",
                "seeded by InventoryConcurrencyIT",
                BigDecimal.TEN,
                null);
        productId = productRepository.save(product).getId();
        inventoryRepository.save(new Inventory(productId, warehouseId, 5));
    }

    /** Reserves one unit, retrying on optimistic-lock contention (CONFLICT) up to 20 times. */
    private void reserveWithRetry() {
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                inventoryService.reserveForOrder(Thread.currentThread().getId(), Map.of(productId, 1));
                return;
            } catch (ApiException e) {
                if (e.getErrorCode() == ErrorCode.CONFLICT) {
                    continue; // contention: retry
                }
                throw e; // INSUFFICIENT_INVENTORY: final
            }
        }
        throw new ApiException(ErrorCode.CONFLICT, "Exhausted client retries");
    }

    @Test
    @DisplayName("100 concurrent reservations against stock=5: exactly 5 succeed, stock never negative")
    void concurrentReservationsNeverOversell() throws Exception {
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    reserveWithRetry();
                    successes.incrementAndGet();
                } catch (ApiException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(5);
        assertThat(rejected.get()).isEqualTo(threads - 5);
        Inventory after = inventoryRepository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow();
        assertThat(after.getAvailableQuantity()).isZero();
        assertThat(after.getReservedQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("sequential reserve and release round-trips quantities")
    void reserveReleaseRoundTrip() {
        inventoryService.reserveForOrder(1L, Map.of(productId, 2));
        Inventory afterReserve = inventoryRepository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow();
        assertThat(afterReserve.getAvailableQuantity()).isEqualTo(3);
        assertThat(afterReserve.getReservedQuantity()).isEqualTo(2);

        inventoryService.releaseForOrder(1L, Map.of(productId, 2));
        Inventory afterRelease = inventoryRepository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow();
        assertThat(afterRelease.getAvailableQuantity()).isEqualTo(5);
        assertThat(afterRelease.getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("reserving more than stock throws INSUFFICIENT_INVENTORY")
    void insufficientStockRejected() {
        assertThatThrownBy(() -> inventoryService.reserveForOrder(2L, Map.of(productId, 6)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY);
    }

    @Test
    @DisplayName("adjustStock refuses negative outcomes")
    void adjustStockGuard() {
        assertThatThrownBy(() -> inventoryService.adjustStock(productId, -1)).isInstanceOf(ApiException.class);
        var dto = inventoryService.adjustStock(productId, 50);
        assertThat(dto.availableQuantity()).isEqualTo(50);
    }
}
