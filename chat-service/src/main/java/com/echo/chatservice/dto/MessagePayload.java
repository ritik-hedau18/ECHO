package com.echo.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePayload {
    private String senderId;
    private String senderUsername;
    private String receiverId;
    private String groupId;
    private String content;
    private String type; // PRIVATE, GROUP
}
