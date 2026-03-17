package com.echo.notificationservice.kafka.consumer;

import com.echo.notificationservice.dto.MessageEvent;
import com.echo.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "chat-messages", groupId = "notification-group")
    public void consumeMessage(MessageEvent event) {
        log.info("Notification Consumer received message event: {}", event);
        try {
            notificationService.processNotification(event);
        } catch (Exception e) {
            log.error("Failed to process notification for message event: {}", event, e);
            throw e; // Trigger DLT
        }
    }
}
