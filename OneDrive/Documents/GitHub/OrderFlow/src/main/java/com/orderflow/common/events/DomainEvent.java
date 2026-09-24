package com.orderflow.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainEvent(
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload,
        String traceId,
        Instant occurredAt) {

    public static DomainEvent of(
            String eventType, String aggregateType, String aggregateId, String payload, String traceId) {
        return new DomainEvent(
                UUID.randomUUID(), eventType, aggregateType, aggregateId, payload, traceId, Instant.now());
    }
}
