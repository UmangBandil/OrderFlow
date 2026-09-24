package com.orderflow.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorCodeMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private int statusFor(ApiException exception) throws Exception {
        var response = handler.handleApiException(exception, stubRequest("/api/v1/test"));
        return response.getStatusCode().value();
    }

    private jakarta.servlet.http.HttpServletRequest stubRequest(String uri) {
        jakarta.servlet.http.HttpServletRequest request =
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    @DisplayName("INSUFFICIENT_INVENTORY maps to 409")
    void insufficientInventory() throws Exception {
        assertThat(statusFor(new ApiException(ErrorCode.INSUFFICIENT_INVENTORY, "x")))
                .isEqualTo(409);
    }

    @Test
    @DisplayName("PAYMENT_FAILED maps to 402")
    void paymentFailed() throws Exception {
        assertThat(statusFor(new ApiException(ErrorCode.PAYMENT_FAILED, "x"))).isEqualTo(402);
    }

    @Test
    @DisplayName("RATE_LIMIT_EXCEEDED maps to 429")
    void rateLimit() throws Exception {
        assertThat(statusFor(new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, "x")))
                .isEqualTo(429);
    }

    @Test
    @DisplayName("SIMULATED_PAYMENT_TIMEOUT maps to 504")
    void timeout() throws Exception {
        assertThat(statusFor(new ApiException(ErrorCode.SIMULATED_PAYMENT_TIMEOUT, "x")))
                .isEqualTo(504);
    }

    @Test
    @DisplayName("ORDER_NOT_CANCELLABLE maps to 409")
    void notCancellable() throws Exception {
        assertThat(statusFor(new ApiException(ErrorCode.ORDER_NOT_CANCELLABLE, "x")))
                .isEqualTo(409);
    }

    @Test
    @DisplayName("error body carries code, status, path and timestamp")
    void bodyShape() throws Exception {
        var response = handler.handleApiException(
                new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found: 1"),
                stubRequest("/api/v1/products/1"));
        GlobalExceptionHandler.ApiErrorBody body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.path()).isEqualTo("/api/v1/products/1");
        assertThat(body.timestamp()).isNotNull();
    }
}
