package com.connecthub.paymentservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String CREDIT_TOPUP_TOPIC = "credit-topup-topic";

    @Bean
    public NewTopic creditTopupTopic() {
        return TopicBuilder.name(CREDIT_TOPUP_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
