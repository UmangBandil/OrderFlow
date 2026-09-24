package com.orderflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orderflow.common.config.KafkaTopicsProperties;
import com.orderflow.support.PostgresContainerSupport;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("itest")
@Tag("integration")
class OutboxRelayIT extends PostgresContainerSupport {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private com.orderflow.common.events.EventPublisher eventPublisher;

    @Autowired
    private JpaTransactionManager transactionManager;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        PostgresContainerSupport.registerContainerProperties(registry);
    }

    @Test
    @DisplayName("publish() inside a transaction persists an outbox row (transactional outbox)")
    void publishPersistsOutboxRow() {
        long suffix = System.nanoTime();
        eventPublisher.publish("test.event", "test", "agg-" + suffix, java.util.Map.of("hello", "world"));

        List<OutboxEvent> rows = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals("agg-" + suffix))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEventType()).isEqualTo("test.event");
        assertThat(rows.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(rows.get(0).getPayload()).contains("hello");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("publisher claims pending rows and marks them processed")
    void publisherRelaysRows() {
        long suffix = System.nanoTime();
        eventPublisher.publish("test.relay", "test", "relay-" + suffix, java.util.Map.of("k", "v"));

        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        OutboxPublisher publisher = new OutboxPublisher(
                outboxEventRepository,
                kafkaTemplate,
                new KafkaTopicsProperties(),
                new TransactionTemplate(transactionManager),
                new OutboxMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                10);
        publisher.publishPending();

        OutboxEvent row = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals("relay-" + suffix))
                .findFirst()
                .orElseThrow();
        assertThat(row.getStatus()).isEqualTo("PROCESSED");
    }
}
