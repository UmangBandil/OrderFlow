package com.orderflow.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment processing and refunds")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public record CreatePaymentRequest(@NotNull Long orderId, @DecimalMin("0.01") BigDecimal amount) {}

    public record PaymentDto(Long id, Long orderId, BigDecimal amount, String status, String providerRef) {

        public static PaymentDto from(Payment payment) {
            return new PaymentDto(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getAmount(),
                    payment.getStatus().name(),
                    payment.getProviderRef());
        }
    }

    @PostMapping
    @Operation(summary = "Create and process a payment for an order")
    public ResponseEntity<PaymentDto> create(@Valid @RequestBody CreatePaymentRequest request) {
        Payment payment = paymentService.processStandalone(request.orderId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentDto.from(payment));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by id")
    public PaymentDto get(@PathVariable Long id) {
        return PaymentDto.from(paymentService.get(id));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a successful payment")
    public ResponseEntity<PaymentDto> refund(@PathVariable Long id, @Valid @RequestBody RefundRequest request) {
        Payment payment = paymentService.refund(id, request.amount(), request.reason());
        return ResponseEntity.ok(PaymentDto.from(payment));
    }

    public record RefundRequest(@DecimalMin("0.01") BigDecimal amount, String reason) {}
}
