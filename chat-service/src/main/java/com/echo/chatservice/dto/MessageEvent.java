package com.echo.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEvent {
    private String eventId;
    private String eventType; // PRIVATE_MESSAGE, GROUP_MESSAGE
    private String senderId;
    private String senderUsername;
    private String receiverId; // For private messages
    private String groupId; // For group messages
    private String content; // encrypted or plaintext depending on client, we send it along
    private LocalDateTime timestamp;
}
