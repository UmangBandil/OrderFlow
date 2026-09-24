package com.orderflow.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    private final KafkaTopicsProperties topics;

    public KafkaTopicConfig(KafkaTopicsProperties topics) {
        this.topics = topics;
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return new NewTopic(topics.getOrderEvents(), 3, (short) 1);
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return new NewTopic(topics.getPaymentEvents(), 3, (short) 1);
    }

    @Bean
    public NewTopic inventoryEventsTopic() {
        return new NewTopic(topics.getInventoryEvents(), 3, (short) 1);
    }

    @Bean
    public NewTopic shipmentEventsTopic() {
        return new NewTopic(topics.getShipmentEvents(), 3, (short) 1);
    }

    @Bean
    public NewTopic orderEventsDltTopic() {
        return new NewTopic(topics.getOrderEventsDlt(), 1, (short) 1);
    }
}
