package com.echo.messagestoreservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDocument {

    @Id
    private String id; // MongoDB document ID

    private String messageId; // Application message UUID
    private String type; // PRIVATE | GROUP
    private String senderId;
    private String senderUsername;
    private String receiverId; // Present if PRIVATE
    private String groupId; // Present if GROUP
    private String content; // AES-256 encrypted content
    private LocalDateTime timestamp;
    private String status; // SENT | DELIVERED | READ
}
