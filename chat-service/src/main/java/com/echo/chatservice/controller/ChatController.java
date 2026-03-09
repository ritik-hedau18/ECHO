package com.echo.chatservice.controller;

import com.echo.chatservice.dto.MessageEvent;
import com.echo.chatservice.dto.MessagePayload;
import com.echo.chatservice.kafka.producer.ChatKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatKafkaProducer kafkaProducer;

    @MessageMapping("/chat.sendMessage")
    public void sendPrivateMessage(@Payload MessagePayload payload, Principal principal) {
        String senderId = principal.getName();
        log.info("Received private message from {} to {}", senderId, payload.getReceiverId());

        MessageEvent event = MessageEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PRIVATE_MESSAGE")
                .senderId(senderId)
                .senderUsername(payload.getSenderUsername())
                .receiverId(payload.getReceiverId())
                .content(payload.getContent())
                .timestamp(LocalDateTime.now())
                .build();

        // Publish to Kafka (for history storage, notifications, and auditing)
        kafkaProducer.sendMessageEvent(event);

        // Broadcast to receiver's private queue
        messagingTemplate.convertAndSendToUser(event.getReceiverId(), "/queue/messages", event);

        // Broadcast back to sender so they see the message delivered
        messagingTemplate.convertAndSendToUser(event.getSenderId(), "/queue/messages", event);
    }

    @MessageMapping("/group.sendMessage")
    public void sendGroupMessage(@Payload MessagePayload payload, Principal principal) {
        String senderId = principal.getName();
        log.info("Received group message from {} to group {}", senderId, payload.getGroupId());

        MessageEvent event = MessageEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("GROUP_MESSAGE")
                .senderId(senderId)
                .senderUsername(payload.getSenderUsername())
                .groupId(payload.getGroupId())
                .content(payload.getContent())
                .timestamp(LocalDateTime.now())
                .build();

        // Publish to Kafka
        kafkaProducer.sendMessageEvent(event);

        // Broadcast to group topic (all users subscribed to /topic/group/{groupId} receive it)
        messagingTemplate.convertAndSend("/topic/group/" + event.getGroupId(), event);
    }
}
