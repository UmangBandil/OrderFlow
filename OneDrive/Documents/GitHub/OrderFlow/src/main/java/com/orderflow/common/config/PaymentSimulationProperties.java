package com.orderflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment-simulation")
public class PaymentSimulationProperties {

    private double failureRate = 0.0;
    private double timeoutRate = 0.0;
    private long latencyMs = 0;

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public double getTimeoutRate() {
        return timeoutRate;
    }

    public void setTimeoutRate(double timeoutRate) {
        this.timeoutRate = timeoutRate;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }
}
