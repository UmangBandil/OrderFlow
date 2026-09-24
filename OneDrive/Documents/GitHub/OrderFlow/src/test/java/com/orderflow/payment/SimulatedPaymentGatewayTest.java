package com.orderflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.common.config.PaymentSimulationProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulatedPaymentGatewayTest {

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(new PaymentSimulationProperties());

    @Test
    @DisplayName("normal amounts are approved")
    void normalAmountApproved() {
        PaymentResult result = gateway.charge("ORD-1", new BigDecimal("100.00"), "ref-1");
        assertThat(result.successful()).isTrue();
        assertThat(result.providerRef()).startsWith("SIM-");
    }

    @Test
    @DisplayName("amount ending .13 always declines (deterministic test hook)")
    void deterministicDecline() {
        PaymentResult result = gateway.charge("ORD-1", new BigDecimal("100.13"), "ref-2");
        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("declined");
    }

    @Test
    @DisplayName("amount ending .14 always times out (deterministic test hook)")
    void deterministicTimeout() {
        PaymentResult result = gateway.charge("ORD-1", new BigDecimal("100.14"), "ref-3");
        assertThat(result.timeout()).isTrue();
        assertThat(result.successful()).isFalse();
    }

    @Test
    @DisplayName("configured failure rate 1.0 always fails")
    void fullFailureRate() {
        PaymentSimulationProperties props = new PaymentSimulationProperties();
        props.setFailureRate(1.0);
        SimulatedPaymentGateway alwaysFails = new SimulatedPaymentGateway(props);
        assertThat(alwaysFails.charge("ORD-1", new BigDecimal("55.00"), "ref-4").successful())
                .isFalse();
    }

    @Test
    @DisplayName("refund succeeds with RE- reference")
    void refundSucceeds() {
        PaymentResult result = gateway.refund("SIM-9", new BigDecimal("25.00"));
        assertThat(result.successful()).isTrue();
        assertThat(result.providerRef()).startsWith("RE-");
    }
}
