package com.orderflow.payment;

public record PaymentResult(boolean successful, boolean timeout, String providerRef, String message) {

    public static PaymentResult success(String providerRef) {
        return new PaymentResult(true, false, providerRef, "Payment approved");
    }

    public static PaymentResult failed(String message) {
        return new PaymentResult(false, false, null, message);
    }

    public static PaymentResult timedOut() {
        return new PaymentResult(false, true, null, "Payment provider timed out");
    }
}
