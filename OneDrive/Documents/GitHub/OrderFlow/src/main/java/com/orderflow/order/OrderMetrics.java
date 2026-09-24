package com.orderflow.order;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter orderCreated;
    private final Counter orderCancelled;
    private final Counter sagaCompensations;

    public OrderMetrics(MeterRegistry registry) {
        this.orderCreated = Counter.builder("orderflow_order_created_total")
                .description("Orders created")
                .register(registry);
        this.orderCancelled = Counter.builder("orderflow_order_cancelled_total")
                .description("Orders cancelled")
                .register(registry);
        this.sagaCompensations = Counter.builder("orderflow_saga_compensation_total")
                .description("Saga compensations executed (inventory release + cancel)")
                .register(registry);
    }

    public void orderCreated() {
        orderCreated.increment();
    }

    public void orderCancelled() {
        orderCancelled.increment();
    }

    public void sagaCompensation() {
        sagaCompensations.increment();
    }
}
