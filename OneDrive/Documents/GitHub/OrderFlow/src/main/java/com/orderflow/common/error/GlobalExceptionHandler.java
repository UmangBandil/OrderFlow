package com.orderflow.common.error;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record FieldError(String field, String message) {}

    public record ApiErrorBody(
            Instant timestamp, int status, String code, String message, String path, List<FieldError> fieldErrors) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorBody> handleApiException(
            ApiException ex, jakarta.servlet.http.HttpServletRequest req) {
        HttpStatus status =
                switch (ex.getErrorCode()) {
                    case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
                    case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
                    case FORBIDDEN -> HttpStatus.FORBIDDEN;
                    case CONFLICT, DUPLICATE_REQUEST -> HttpStatus.CONFLICT;
                    case INSUFFICIENT_INVENTORY -> HttpStatus.CONFLICT;
                    case PAYMENT_FAILED -> HttpStatus.PAYMENT_REQUIRED;
                    case ORDER_NOT_CANCELLABLE -> HttpStatus.CONFLICT;
                    case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
                    case SIMULATED_PAYMENT_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
                };
        return respond(status, ex.getErrorCode().name(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorBody> handleValidation(
            MethodArgumentNotValidException ex, jakarta.servlet.http.HttpServletRequest req) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Validation failed", req, fieldErrors);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorBody> handleNotFound(
            NoSuchElementException ex, jakarta.servlet.http.HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", req, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorBody> handleOptimisticLock(
            OptimisticLockingFailureException ex, jakarta.servlet.http.HttpServletRequest req) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "Concurrent modification detected, please retry", req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorBody> handleDataIntegrity(
            DataIntegrityViolationException ex, jakarta.servlet.http.HttpServletRequest req) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return respond(HttpStatus.CONFLICT, "CONFLICT", "Request conflicts with current state", req, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorBody> handleBadRequest(Exception ex, jakarta.servlet.http.HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed request", req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNoResource(
            NoResourceFoundException ex, jakarta.servlet.http.HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", req, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalState(
            IllegalStateException ex, jakarta.servlet.http.HttpServletRequest req) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), req, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorBody> handleBadCredentials(
            BadCredentialsException ex, jakarta.servlet.http.HttpServletRequest req) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorBody> handleAccessDenied(
            AccessDeniedException ex, jakarta.servlet.http.HttpServletRequest req) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleGeneric(Exception ex, jakarta.servlet.http.HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", req, null);
    }

    private ResponseEntity<ApiErrorBody> respond(
            HttpStatus status,
            String code,
            String message,
            jakarta.servlet.http.HttpServletRequest req,
            List<FieldError> fieldErrors) {
        ApiErrorBody body =
                new ApiErrorBody(Instant.now(), status.value(), code, message, req.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
