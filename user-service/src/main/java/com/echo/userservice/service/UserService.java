package com.echo.userservice.service;

import com.echo.userservice.dto.*;
import com.echo.userservice.entity.RefreshToken;
import com.echo.userservice.entity.User;
import com.echo.userservice.exception.CustomException;
import com.echo.userservice.kafka.producer.UserEventProducer;
import com.echo.userservice.repository.RefreshTokenRepository;
import com.echo.userservice.repository.UserRepository;
import com.echo.userservice.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final UserEventProducer userEventProducer;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new CustomException("Username is already taken", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new CustomException("Email is already registered", HttpStatus.BAD_REQUEST);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .avatarUrl(request.getAvatarUrl())
                .build();

        user = userRepository.save(user);

        // Publish event
        userEventProducer.sendUserEvent(UserEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("REGISTRATION")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .timestamp(LocalDateTime.now())
                .build());

        return generateTokensForUser(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new CustomException("Invalid username or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomException("Invalid username or password", HttpStatus.UNAUTHORIZED);
        }

        // Publish event
        userEventProducer.sendUserEvent(UserEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("LOGIN")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .timestamp(LocalDateTime.now())
                .build());

        return generateTokensForUser(user);
    }

    @Transactional
    public TokenResponse refresh(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (refreshToken.isRevoked() || refreshToken.getExpiry().isBefore(Instant.now())) {
            throw new CustomException("Refresh token is expired or revoked", HttpStatus.UNAUTHORIZED);
        }

        // Revoke the old token (Refresh Token Rotation!)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        User user = refreshToken.getUser();
        return generateTokensForUser(user);
    }

    @Transactional
    public void logout(String authHeader, String refreshTokenString) {
        String userId = null;
        String username = null;
        String email = null;

        if (refreshTokenString != null) {
            RefreshToken token = refreshTokenRepository.findByToken(refreshTokenString).orElse(null);
            if (token != null) {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
                userId = token.getUser().getId();
                username = token.getUser().getUsername();
                email = token.getUser().getEmail();
            }
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            try {
                if (tokenProvider.validateToken(accessToken)) {
                    String jti = tokenProvider.getJtiFromToken(accessToken);
                    long expiryTimeMs = tokenProvider.getExpirationTimeMs(accessToken);
                    long remainingTimeMs = expiryTimeMs - System.currentTimeMillis();

                    if (userId == null) {
                        userId = tokenProvider.getUserIdFromToken(accessToken);
                        User user = userRepository.findById(userId).orElse(null);
                        if (user != null) {
                            username = user.getUsername();
                            email = user.getEmail();
                        }
                    }

                    if (remainingTimeMs > 0) {
                        String redisKey = "blacklist:token:" + jti;
                        redisTemplate.opsForValue().set(redisKey, "revoked", remainingTimeMs, TimeUnit.MILLISECONDS);
                        log.info("Blacklisted JWT with key {} for {} ms", redisKey, remainingTimeMs);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to blacklist access token during logout", e);
            }
        }

        // Publish event
        if (userId != null) {
            userEventProducer.sendUserEvent(UserEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("LOGOUT")
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }

    public UserResponse getUserProfile(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        return mapToUserResponse(user);
    }

    public List<UserResponse> searchUsers(String query) {
        return userRepository.findByUsernameContainingIgnoreCase(query).stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    private TokenResponse generateTokensForUser(User user) {
        String jti = UUID.randomUUID().toString();
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getEmail(), jti);

        String refreshTokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiry(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    public void heartbeat(String userId) {
        String redisKey = "online:" + userId;
        redisTemplate.opsForValue().set(redisKey, "true", 30, TimeUnit.SECONDS);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
