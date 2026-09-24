package com.orderflow.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final Counter completed;
    private final Counter failed;

    public PaymentMetrics(MeterRegistry registry) {
        this.completed = Counter.builder("orderflow_payment_completed_total")
                .description("Completed payments")
                .register(registry);
        this.failed = Counter.builder("orderflow_payment_failed_total")
                .description("Failed payments")
                .register(registry);
    }

    public void paymentCompleted() {
        completed.increment();
    }

    public void paymentFailed() {
        failed.increment();
    }
}
