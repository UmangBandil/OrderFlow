package com.orderflow.outbox;

import com.orderflow.common.config.KafkaTopicsProperties;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    private final TransactionTemplate transactionTemplate;
    private final OutboxMetrics metrics;
    private final int batchSize;

    public OutboxPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaTopicsProperties topics,
            TransactionTemplate transactionTemplate,
            OutboxMetrics metrics,
            @Value("${app.outbox.batch-size:50}") int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void publishPending() {
        List<OutboxEvent> events =
                transactionTemplate.execute(tx -> repository.claimBatch(PageRequest.of(0, batchSize)));
        if (events == null || events.isEmpty()) {
            return;
        }
        for (OutboxEvent event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate
                        .send(topic, event.getAggregateId(), buildEnvelope(event))
                        .get(5, TimeUnit.SECONDS);
                event.markProcessed();
                metrics.published();
            } catch (Exception e) {
                metrics.failure();
                event.incrementAttempts();
                if (event.getAttempts() >= MAX_ATTEMPTS) {
                    event.markFailed();
                    log.error("Outbox event {} failed after {} attempts", event.getEventId(), event.getAttempts(), e);
                } else {
                    log.warn(
                            "Outbox event {} publish attempt {} failed: {}",
                            event.getEventId(),
                            event.getAttempts(),
                            e.getMessage());
                }
            }
        }
        transactionTemplate.executeWithoutResult(tx -> repository.saveAll(events));
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "payment.completed", "payment.failed", "refund.created" -> topics.getPaymentEvents();
            case "inventory.reserved", "inventory.released" -> topics.getInventoryEvents();
            case "shipment.created", "shipment.delivered", "shipment.status_changed" -> topics.getShipmentEvents();
            default -> topics.getOrderEvents();
        };
    }

    private String buildEnvelope(OutboxEvent event) {
        return "{\"eventId\":\"" + event.getEventId()
                + "\",\"eventType\":\"" + event.getEventType()
                + "\",\"aggregateType\":\"" + event.getAggregateType()
                + "\",\"aggregateId\":\"" + event.getAggregateId()
                + "\",\"payload\":" + event.getPayload()
                + ",\"traceId\":\"" + event.getTraceId()
                + "\",\"occurredAt\":\"" + event.getCreatedAt()
                + "\"}";
    }
}
