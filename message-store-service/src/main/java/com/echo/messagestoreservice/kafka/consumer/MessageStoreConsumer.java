package com.echo.messagestoreservice.kafka.consumer;

import com.echo.messagestoreservice.dto.MessageEvent;
import com.echo.messagestoreservice.entity.MessageDocument;
import com.echo.messagestoreservice.repository.MessageRepository;
import com.echo.messagestoreservice.security.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageStoreConsumer {

    private final MessageRepository messageRepository;
    private final EncryptionUtil encryptionUtil;

    @KafkaListener(topics = "chat-messages", groupId = "message-store-group")
    public void consumeMessage(MessageEvent event) {
        log.info("Message Store Consumer received event: {}", event);
        try {
            String encryptedContent = encryptionUtil.encrypt(event.getContent());
            String type = event.getReceiverId() != null ? "PRIVATE" : "GROUP";

            MessageDocument doc = MessageDocument.builder()
                    .messageId(event.getEventId())
                    .type(type)
                    .senderId(event.getSenderId())
                    .senderUsername(event.getSenderUsername())
                    .receiverId(event.getReceiverId())
                    .groupId(event.getGroupId())
                    .content(encryptedContent)
                    .timestamp(event.getTimestamp())
                    .status("SENT")
                    .build();

            messageRepository.save(doc);
            log.info("Saved message document to MongoDB with UUID: {}", doc.getMessageId());

        } catch (Exception e) {
            log.error("Failed to process and store chat message: {}", event, e);
            throw e; // Propagate exception to trigger Kafka error handler (DLT)
        }
    }
}
