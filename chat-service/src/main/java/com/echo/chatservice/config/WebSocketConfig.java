package com.echo.chatservice.config;

import com.echo.chatservice.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TokenProvider tokenProvider;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    log.info("WebSocket connection attempt with Authorization header: {}", authHeader);
                    
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        if (tokenProvider.validateToken(token)) {
                            String userId = tokenProvider.getUserIdFromToken(token);
                            String username = tokenProvider.getUsernameFromToken(token);
                            
                            Principal principal = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                            accessor.setUser(principal);
                            
                            if (accessor.getSessionAttributes() == null) {
                                accessor.setSessionAttributes(new HashMap<>());
                            }
                            accessor.getSessionAttributes().put("userId", userId);
                            accessor.getSessionAttributes().put("username", username);
                            
                            log.info("WebSocket user connected. ID: {}, Name: {}", userId, username);
                        } else {
                            log.warn("Invalid JWT token provided in WebSocket CONNECT");
                            throw new IllegalArgumentException("Invalid JWT token");
                        }
                    } else {
                        log.warn("No JWT token provided in WebSocket CONNECT");
                        throw new IllegalArgumentException("No JWT token");
                    }
                }
                return message;
            }
        });
    }
}
