package com.orderflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import com.orderflow.common.events.EventPublisher;
import com.orderflow.order.Order;
import com.orderflow.order.OrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentGateway gateway;
    private EventPublisher eventPublisher;
    private OrderRepository orderRepository;
    private PaymentService paymentService;
    private Order order;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        gateway = mock(PaymentGateway.class);
        eventPublisher = mock(EventPublisher.class);
        orderRepository = mock(OrderRepository.class);
        paymentService = new PaymentService(
                paymentRepository,
                gateway,
                eventPublisher,
                new PaymentMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                orderRepository);
        order = new Order("ORD-20260925-TEST0001", 7L, new BigDecimal("100.00"), "Street 1, Pune 411001");
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", 1L);
    }

    @Test
    @DisplayName("successful charge marks payment SUCCESS")
    void successfulCharge() {
        when(paymentRepository.findByProviderRef(anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.charge(anyString(), any(), anyString())).thenReturn(PaymentResult.success("SIM-1"));

        Payment payment = paymentService.processForOrder(order, new BigDecimal("100.00"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(gateway, times(1)).charge(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("declined card throws PAYMENT_FAILED and marks payment FAILED")
    void declinedCharge() {
        when(paymentRepository.findByProviderRef(anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.charge(anyString(), any(), anyString())).thenReturn(PaymentResult.failed("Card declined"));

        assertThatThrownBy(() -> paymentService.processForOrder(order, new BigDecimal("100.00")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("gateway timeout is retried then throws SIMULATED_PAYMENT_TIMEOUT")
    void timeoutRetriesThenFails() {
        when(paymentRepository.findByProviderRef(anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.charge(anyString(), any(), anyString())).thenReturn(PaymentResult.timedOut());

        assertThatThrownBy(() -> paymentService.processForOrder(order, new BigDecimal("100.00")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.SIMULATED_PAYMENT_TIMEOUT);
        verify(gateway, times(3)).charge(anyString(), any(), anyString()); // 1 + 2 retries
    }

    @Test
    @DisplayName("timeout then success recovers within retries")
    void timeoutThenSuccess() {
        when(paymentRepository.findByProviderRef(anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.charge(anyString(), any(), anyString()))
                .thenReturn(PaymentResult.timedOut())
                .thenReturn(PaymentResult.success("SIM-2"));

        Payment payment = paymentService.processForOrder(order, new BigDecimal("100.00"));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(gateway, times(2)).charge(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("duplicate payment request returns existing payment without recharging")
    void duplicateRequestIsIdempotent() {
        Payment existing = new Payment(1L, new BigDecimal("100.00"), "PR-ORD-20260925-TEST0001");
        existing.markSuccess();
        when(paymentRepository.findByProviderRef("PR-ORD-20260925-TEST0001")).thenReturn(Optional.of(existing));

        Payment payment = paymentService.processForOrder(order, new BigDecimal("100.00"));

        assertThat(payment).isSameAs(existing);
        verify(gateway, never()).charge(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("refund of successful payment marks REFUNDED and publishes refund.created")
    void refundSuccessful() {
        Payment payment = new Payment(1L, new BigDecimal("100.00"), "SIM-3");
        org.springframework.test.util.ReflectionTestUtils.setField(payment, "id", 55L);
        payment.markSuccess();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(gateway.refund(eq("SIM-3"), any())).thenReturn(PaymentResult.success("RE-1"));

        Payment refunded = paymentService.refund(1L, new BigDecimal("100.00"), "damaged");

        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(eventPublisher).publish(eq("refund.created"), eq("payment"), eq(String.valueOf(payment.getId())), any());
    }

    @Test
    @DisplayName("refund of non-successful payment is rejected")
    void refundRejectedWhenNotSuccessful() {
        Payment payment = new Payment(1L, new BigDecimal("100.00"), "SIM-4");
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refund(1L, new BigDecimal("100.00"), "test"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("refund above original amount is rejected")
    void refundAboveAmountRejected() {
        Payment payment = new Payment(1L, new BigDecimal("50.00"), "SIM-5");
        payment.markSuccess();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refund(1L, new BigDecimal("51.00"), "test"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
