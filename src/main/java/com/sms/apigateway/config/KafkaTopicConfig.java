package com.sms.apigateway.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String ALERT_FORGOTPWD_TOPIC = "alert.forgot.password";

    @Bean
    public NewTopic resetPasswordLinkTopic() {
        return TopicBuilder.name(ALERT_FORGOTPWD_TOPIC)
                .partitions(3)
                .replicas(1) // single-broker local dev default - raise this for a real cluster
                .build();
    }

}
