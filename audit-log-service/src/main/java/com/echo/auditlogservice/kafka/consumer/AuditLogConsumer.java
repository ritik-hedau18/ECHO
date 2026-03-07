package com.echo.auditlogservice.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class AuditLogConsumer {

    @KafkaListener(topics = "chat-messages", groupId = "audit-log-group")
    public void consumeChatMessages(@Payload Map<String, Object> messageEvent) {
        log.info("AUDIT_CHAT: eventType={}, eventId={}, senderId={}, senderUsername={}, receiverId={}, groupId={}, timestamp={}",
                messageEvent.get("eventType"),
                messageEvent.get("eventId"),
                messageEvent.get("senderId"),
                messageEvent.get("senderUsername"),
                messageEvent.get("receiverId"),
                messageEvent.get("groupId"),
                messageEvent.get("timestamp"));
    }

    @KafkaListener(topics = "user-events", groupId = "audit-log-group")
    public void consumeUserEvents(@Payload Map<String, Object> userEvent) {
        log.info("AUDIT_USER: eventType={}, eventId={}, userId={}, username={}, email={}, timestamp={}",
                userEvent.get("eventType"),
                userEvent.get("eventId"),
                userEvent.get("userId"),
                userEvent.get("username"),
                userEvent.get("email"),
                userEvent.get("timestamp"));
    }

    @KafkaListener(topics = "group-events", groupId = "audit-log-group")
    public void consumeGroupEvents(@Payload Map<String, Object> groupEvent) {
        log.info("AUDIT_GROUP: eventType={}, eventId={}, groupId={}, groupName={}, userId={}, role={}, timestamp={}",
                groupEvent.get("eventType"),
                groupEvent.get("eventId"),
                groupEvent.get("groupId"),
                groupEvent.get("groupName"),
                groupEvent.get("userId"),
                groupEvent.get("role"),
                groupEvent.get("timestamp"));
    }
}
