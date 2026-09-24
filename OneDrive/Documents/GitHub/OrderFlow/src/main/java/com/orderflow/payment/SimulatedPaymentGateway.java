package com.orderflow.payment;

import com.orderflow.common.config.PaymentSimulationProperties;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulated payment provider. Deterministic test hooks:
 * - amount ending in .13 -> always fails
 * - amount ending in .14 -> always times out
 * Otherwise behaves according to configured failure/timeout rates.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);
    private static final BigDecimal FAIL_AMOUNT = new BigDecimal("0.13");
    private static final BigDecimal TIMEOUT_AMOUNT = new BigDecimal("0.14");

    private final PaymentSimulationProperties props;

    public SimulatedPaymentGateway(PaymentSimulationProperties props) {
        this.props = props;
    }

    @Override
    public PaymentResult charge(String orderNumber, BigDecimal amount, String idempotencyRef) {
        log.info("SIMULATED CHARGE: order={} amount={} ref={}", orderNumber, amount, idempotencyRef);
        sleepIfConfigured();

        BigDecimal fraction = amount.remainder(BigDecimal.ONE);
        if (fraction.compareTo(FAIL_AMOUNT) == 0) {
            return PaymentResult.failed("Card declined");
        }
        if (fraction.compareTo(TIMEOUT_AMOUNT) == 0) {
            return PaymentResult.timedOut();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (props.getTimeoutRate() > 0 && random.nextDouble() < props.getTimeoutRate()) {
            return PaymentResult.timedOut();
        }
        if (props.getFailureRate() > 0 && random.nextDouble() < props.getFailureRate()) {
            return PaymentResult.failed("Random simulated failure");
        }
        return PaymentResult.success("SIM-" + UUID.randomUUID());
    }

    @Override
    public PaymentResult refund(String providerRef, BigDecimal amount) {
        log.info("SIMULATED REFUND: ref={} amount={}", providerRef, amount);
        return PaymentResult.success("RE-" + UUID.randomUUID());
    }

    private void sleepIfConfigured() {
        if (props.getLatencyMs() > 0) {
            try {
                Thread.sleep(props.getLatencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
