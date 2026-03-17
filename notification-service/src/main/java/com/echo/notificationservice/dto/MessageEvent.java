package com.echo.notificationservice.dto;

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
    private String receiverId;
    private String groupId;
    private String content; // encrypted or plaintext
    private LocalDateTime timestamp;
}
