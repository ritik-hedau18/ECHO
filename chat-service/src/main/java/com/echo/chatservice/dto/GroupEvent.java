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
public class GroupEvent {
    private String eventId;
    private String eventType; // GROUP_CREATE, GROUP_JOIN, GROUP_LEAVE
    private String groupId;
    private String groupName;
    private String userId;
    private String role;
    private LocalDateTime timestamp;
}
