package com.platform.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    @Bean public NewTopic orderEvents() { return TopicBuilder.name("order-events").partitions(3).replicas(1).build(); }
    @Bean public NewTopic inventoryEvents() { return TopicBuilder.name("inventory-events").partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentEvents() { return TopicBuilder.name("payment-events").partitions(3).replicas(1).build(); }
    @Bean public NewTopic notificationEvents() { return TopicBuilder.name("notification-events").partitions(3).replicas(1).build(); }
}
