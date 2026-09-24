package com.orderflow.payment;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import com.orderflow.common.error.ResourceNotFoundException;
import com.orderflow.common.events.EventPublisher;
import com.orderflow.order.Order;
import com.orderflow.order.OrderRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int TIMEOUT_RETRIES = 2;

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final EventPublisher eventPublisher;
    private final PaymentMetrics paymentMetrics;
    private final OrderRepository orderRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway,
            EventPublisher eventPublisher,
            PaymentMetrics paymentMetrics,
            OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.paymentMetrics = paymentMetrics;
        this.orderRepository = orderRepository;
    }

    /**
     * Processes payment for an order. Retries on provider timeout, fails fast on decline.
     * Uses providerRef for duplicate detection at the gateway level.
     */
    @Transactional
    public Payment processForOrder(com.orderflow.order.Order order, BigDecimal amount) {
        String providerRef = "PR-" + order.getOrderNumber();
        Payment payment = paymentRepository
                .findByProviderRef(providerRef)
                .orElseGet(() -> paymentRepository.save(new Payment(order.getId(), amount, providerRef)));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed with status {}", providerRef, payment.getStatus());
            return payment; // duplicate request: return existing payment
        }

        PaymentResult result = chargeWithRetry(order.getOrderNumber(), amount, providerRef);

        if (result.successful()) {
            payment.markSuccess();
            paymentMetrics.paymentCompleted();
        } else if (result.timeout()) {
            payment.markFailed();
            paymentMetrics.paymentFailed();
            throw new ApiException(ErrorCode.SIMULATED_PAYMENT_TIMEOUT, "Payment provider timed out");
        } else {
            payment.markFailed();
            paymentMetrics.paymentFailed();
            throw new ApiException(ErrorCode.PAYMENT_FAILED, result.message());
        }
        return payment;
    }

    private PaymentResult chargeWithRetry(String orderNumber, BigDecimal amount, String providerRef) {
        PaymentResult result = null;
        for (int attempt = 0; attempt <= TIMEOUT_RETRIES; attempt++) {
            result = paymentGateway.charge(orderNumber, amount, providerRef);
            if (!result.timeout()) {
                return result;
            }
            log.warn("Payment timeout for order {}, attempt {}/{}", orderNumber, attempt + 1, TIMEOUT_RETRIES + 1);
        }
        return result;
    }

    @Transactional
    public Payment refund(Long paymentId, BigDecimal amount, String reason) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ApiException(ErrorCode.CONFLICT, "Only successful payments can be refunded");
        }
        if (amount.compareTo(payment.getAmount()) > 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Refund amount exceeds payment amount");
        }
        PaymentResult result = paymentGateway.refund(payment.getProviderRef(), amount);
        if (!result.successful()) {
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Refund failed: " + result.message());
        }
        payment.markRefunded();
        eventPublisher.publish(
                "refund.created",
                "payment",
                String.valueOf(payment.getId()),
                Map.of(
                        "paymentId",
                        payment.getId(),
                        "orderId",
                        payment.getOrderId(),
                        "amount",
                        amount,
                        "reason",
                        reason == null ? "" : reason));
        return payment;
    }

    /** Standalone payment for an already-confirmed order (used by the payments API). */
    @Transactional
    public Payment processStandalone(Long orderId, BigDecimal amount) {
        Order order =
                orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return processForOrder(order, amount);
    }

    @Transactional(readOnly = true)
    public Payment get(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }
}
