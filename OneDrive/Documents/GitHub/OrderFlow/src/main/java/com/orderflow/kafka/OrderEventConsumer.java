package com.orderflow.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.notification.NotificationService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes order lifecycle events. Idempotent by eventId (in-memory dedupe window);
 * failures after retries are routed to the DLT by the configured error handler.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private static final int DEDUPE_CACHE_SIZE = 10_000;

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final Map<String, Boolean> seenEvents = new ConcurrentHashMap<>();

    public OrderEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${orderflow.topics.order-events}", groupId = "orderflow-core")
    public void onOrderEvent(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventId = envelope.path("eventId").asText();
            String eventType = envelope.path("eventType").asText();

            if (seenEvents.putIfAbsent(eventId, Boolean.TRUE) != null) {
                log.debug("Duplicate event {} ignored", eventId);
                return;
            }
            if (seenEvents.size() > DEDUPE_CACHE_SIZE) {
                seenEvents.clear(); // simple bounded dedupe for the simulator
            }

            log.info(
                    "ORDER EVENT on {}: {} for aggregate {}",
                    topic,
                    eventType,
                    envelope.path("aggregateId").asText());

            String orderNumber = envelope.path("aggregateId").asText();
            switch (eventType) {
                case "order.confirmed" -> notificationService.sendEmail(
                        "customer@example.com", "ORDER_CONFIRMED", orderNumber);
                case "payment.failed" -> notificationService.sendEmail(
                        "customer@example.com", "PAYMENT_FAILED", orderNumber);
                case "order.cancelled" -> notificationService.sendEmail(
                        "customer@example.com", "ORDER_CANCELLED", orderNumber);
                default -> log.debug("No notification routing for {}", eventType);
            }
        } catch (Exception e) {
            // Rethrow so the error handler retries and finally DLTs the message
            throw new IllegalStateException("Failed to process order event: " + e.getMessage(), e);
        }
    }
}
