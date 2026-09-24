package com.orderflow.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "response_body", nullable = false, columnDefinition = "text")
    private String responseBody;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyKey() {}

    public IdempotencyKey(String idempotencyKey, Long userId, String endpoint, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.endpoint = endpoint;
        this.responseBody = responseBody;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
