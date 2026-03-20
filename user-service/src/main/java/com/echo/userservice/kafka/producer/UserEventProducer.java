package com.echo.userservice.kafka.producer;

import com.echo.userservice.dto.UserEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventProducer {

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    private static final String TOPIC = "user-events";

    public void sendUserEvent(UserEvent event) {
        log.info("Publishing user event to Kafka: {}", event);
        try {
            kafkaTemplate.send(TOPIC, event.getUserId(), event);
        } catch (Exception e) {
            log.error("Failed to send user event to Kafka", e);
        }
    }
}
