package com.sms.apigateway.producer;

import com.sms.apigateway.event.UserEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertForgotPasswordEventProducer {

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;

    public AlertForgotPasswordEventProducer(KafkaTemplate<String, UserEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void alertForgotPassword(UserEvent event) {
        kafkaTemplate.send("alert.forgot.password", event);
    }
}

