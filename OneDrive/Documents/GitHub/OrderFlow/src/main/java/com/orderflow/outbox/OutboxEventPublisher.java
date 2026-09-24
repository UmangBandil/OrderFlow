package com.orderflow.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.events.EventPublisher;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes domain events into the outbox table inside the caller's transaction.
 * The scheduled OutboxPublisher relays them to Kafka afterwards (at-least-once).
 */
@Component
public class OutboxEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(String eventType, String aggregateType, String aggregateId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEvent event =
                    new OutboxEvent(UUID.randomUUID(), aggregateType, aggregateId, eventType, json, currentTraceId());
            repository.save(event);
            log.debug("Outbox event queued: {} for {}/{}", eventType, aggregateType, aggregateId);
        } catch (Exception e) {
            // Never let event serialization break the business transaction; log loudly instead.
            log.error("Failed to enqueue outbox event {}: {}", eventType, e.getMessage(), e);
        }
    }

    private String currentTraceId() {
        return null; // wire in Micrometer tracing when tracing infra is added
    }
}
