package com.orderflow.payment;

import java.math.BigDecimal;

/** Abstraction over the (simulated) payment provider. */
public interface PaymentGateway {

    PaymentResult charge(String orderNumber, BigDecimal amount, String idempotencyRef);

    PaymentResult refund(String providerRef, BigDecimal amount);
}
