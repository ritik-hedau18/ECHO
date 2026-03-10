package com.echo.chatservice.kafka.producer;

import com.echo.chatservice.dto.GroupEvent;
import com.echo.chatservice.dto.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String CHAT_TOPIC = "chat-messages";
    private static final String GROUP_TOPIC = "group-events";

    public void sendMessageEvent(MessageEvent event) {
        log.info("Publishing chat message event to Kafka: {}", event);
        try {
            // Partition by senderId or receiverId/groupId to preserve order of conversation
            String partitionKey = event.getGroupId() != null ? event.getGroupId() : event.getSenderId();
            kafkaTemplate.send(CHAT_TOPIC, partitionKey, event);
        } catch (Exception e) {
            log.error("Failed to publish message event to Kafka", e);
        }
    }

    public void sendGroupEvent(GroupEvent event) {
        log.info("Publishing group event to Kafka: {}", event);
        try {
            kafkaTemplate.send(GROUP_TOPIC, event.getGroupId(), event);
        } catch (Exception e) {
            log.error("Failed to publish group event to Kafka", e);
        }
    }
}
