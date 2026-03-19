package com.echo.notificationservice.service;

import com.echo.notificationservice.dto.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;

    public void processNotification(MessageEvent event) {
        if ("PRIVATE_MESSAGE".equals(event.getEventType())) {
            String receiverId = event.getReceiverId();
            boolean isOnline = checkIfUserOnline(receiverId);

            if (!isOnline) {
                log.info("User {} is OFFLINE. Triggering push notification and email alert.", receiverId);
                sendPushNotification(receiverId, event);
                sendEmailAlert(receiverId, event);
            } else {
                log.info("User {} is ONLINE. Skipping offline alerts.", receiverId);
            }
        } else {
            log.info("Received group message event for group {}. Skipping standard personal offline check.", event.getGroupId());
        }
    }

    private boolean checkIfUserOnline(String userId) {
        String redisKey = "online:" + userId;
        Boolean hasKey = redisTemplate.hasKey(redisKey);
        return hasKey != null && hasKey;
    }

    private void sendPushNotification(String userId, MessageEvent event) {
        log.info("[FCM PUSH NOTIFICATION] To: User {}, Body: 'New message from {}: {}'",
                userId, event.getSenderUsername(), event.getContent());
    }

    private void sendEmailAlert(String userId, MessageEvent event) {
        String recipientEmail = "user_" + userId + "@echochat.com";
        String subject = "New Missed Message on ECHO from " + event.getSenderUsername();
        String text = String.format("Hello!\n\nYou missed a message from %s at %s.\n\nMessage content: %s\n\nLog in to ECHO to reply!",
                event.getSenderUsername(), event.getTimestamp(), event.getContent());

        log.info("[Spring Mail] Attempting to send email to {}", recipientEmail);
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(recipientEmail);
            mailMessage.setSubject(subject);
            mailMessage.setText(text);
            mailMessage.setFrom("echo.notifications.chat@gmail.com");

            mailSender.send(mailMessage);
            log.info("[Spring Mail] Email sent successfully to {}", recipientEmail);
        } catch (Exception e) {
            log.warn("[Spring Mail Simulation] Could not dispatch actual SMTP mail. Simulated Email payload below:\n" +
                    "=========================================\n" +
                    "To: {}\n" +
                    "Subject: {}\n" +
                    "Body: {}\n" +
                    "=========================================", recipientEmail, subject, text);
        }
    }
}
