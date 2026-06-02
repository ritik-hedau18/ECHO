package com.echo.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {
    private String eventId;
    private String eventType; // REGISTRATION, LOGIN, LOGOUT
    private String userId;
    private String username;
    private String email;
    private LocalDateTime timestamp;
}
