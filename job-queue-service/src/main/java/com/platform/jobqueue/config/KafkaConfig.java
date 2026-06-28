package com.platform.jobqueue.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic jobSubmittedTopic() {
        return new NewTopic("job-submitted", 3, (short) 1);
    }

    @Bean
    public NewTopic jobProcessingTopic() {
        return new NewTopic("job-processing", 3, (short) 1);
    }

    @Bean
    public NewTopic jobCompletedTopic() {
        return new NewTopic("job-completed", 3, (short) 1);
    }

    @Bean
    public NewTopic jobFailedTopic() {
        return new NewTopic("job-failed", 3, (short) 1);
    }

    @Bean
    public NewTopic jobDlqTopic() {
        return new NewTopic("job-dlq", 1, (short) 1);
    }
}
