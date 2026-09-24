package com.orderflow.inventory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InventoryMetrics {

    private final Counter reserveSuccess;
    private final Counter reserveFailure;

    public InventoryMetrics(MeterRegistry registry) {
        this.reserveSuccess = Counter.builder("orderflow_inventory_reserved_total")
                .description("Successful inventory reservations")
                .register(registry);
        this.reserveFailure = Counter.builder("orderflow_inventory_failed_total")
                .description("Failed inventory reservations")
                .register(registry);
    }

    public void reservationSucceeded() {
        reserveSuccess.increment();
    }

    public void reservationFailed() {
        reserveFailure.increment();
    }
}
