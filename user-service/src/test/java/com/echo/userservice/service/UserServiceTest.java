package com.echo.userservice.service;

import com.echo.userservice.dto.LoginRequest;
import com.echo.userservice.dto.RegisterRequest;
import com.echo.userservice.dto.TokenResponse;
import com.echo.userservice.entity.User;
import com.echo.userservice.exception.CustomException;
import com.echo.userservice.kafka.producer.UserEventProducer;
import com.echo.userservice.repository.RefreshTokenRepository;
import com.echo.userservice.repository.UserRepository;
import com.echo.userservice.security.TokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenProvider tokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private UserEventProducer userEventProducer;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id("test-id")
                .username("testuser")
                .email("test@echo.com")
                .passwordHash("hashed-password")
                .build();
    }

    @Test
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .email("test@echo.com")
                .password("password")
                .build();

        when(userRepository.findByUsername(request.getUsername())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(tokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("access-token");

        TokenResponse response = userService.register(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("testuser", response.getUsername());
        verify(userRepository, times(1)).save(any(User.class));
        verify(userEventProducer, times(1)).sendUserEvent(any());
    }

    @Test
    void register_UsernameTaken_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .username("testuser")
                .email("test@echo.com")
                .password("password")
                .build();

        when(userRepository.findByUsername(request.getUsername())).thenReturn(Optional.of(sampleUser));

        assertThrows(CustomException.class, () -> userService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("password")
                .build();

        when(userRepository.findByUsername(request.getUsername())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(request.getPassword(), sampleUser.getPasswordHash())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("access-token");

        TokenResponse response = userService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        verify(userEventProducer, times(1)).sendUserEvent(any());
    }

    @Test
    void login_WrongPassword_ThrowsException() {
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("wrongpassword")
                .build();

        when(userRepository.findByUsername(request.getUsername())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(request.getPassword(), sampleUser.getPasswordHash())).thenReturn(false);

        assertThrows(CustomException.class, () -> userService.login(request));
    }
}
