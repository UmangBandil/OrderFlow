package com.orderflow.common.events;

/** Modules publish domain events through this abstraction (implemented via the transactional outbox). */
public interface EventPublisher {

    void publish(String eventType, String aggregateType, String aggregateId, Object payload);
}
