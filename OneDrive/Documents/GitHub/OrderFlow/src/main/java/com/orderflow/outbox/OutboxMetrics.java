package com.orderflow.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    private final Counter published;
    private final Counter failures;

    public OutboxMetrics(MeterRegistry registry) {
        this.published = Counter.builder("orderflow_outbox_published_total")
                .description("Outbox events published to Kafka")
                .register(registry);
        this.failures = Counter.builder("orderflow_outbox_failures_total")
                .description("Outbox publish failures")
                .register(registry);
    }

    public void published() {
        published.increment();
    }

    public void failure() {
        failures.increment();
    }
}
