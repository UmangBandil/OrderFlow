package com.orderflow.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, length = 16)
    private String status;

    protected OutboxEvent() {}

    public OutboxEvent(
            UUID eventId, String aggregateType, String aggregateId, String eventType, String payload, String traceId) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.traceId = traceId;
        this.createdAt = OffsetDateTime.now();
        this.attempts = 0;
        this.status = "PENDING";
    }

    public void markProcessed() {
        this.status = "PROCESSED";
        this.processedAt = OffsetDateTime.now();
    }

    public void markFailed() {
        this.status = "FAILED";
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getTraceId() {
        return traceId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getStatus() {
        return status;
    }
}
