package com.qinglian.fitness.auth;

import com.qinglian.fitness.config.AppProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.OptionalLong;

@Service
public class SessionService {

    private final SessionRepository repository;
    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionService(SessionRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public CreatedSession create(long userId) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = Instant.now().plus(properties.sessionTtl());
        repository.create(userId, hash(rawToken), expiresAt);
        return new CreatedSession(rawToken, expiresAt);
    }

    public OptionalLong verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return OptionalLong.empty();
        }
        String tokenHash = hash(rawToken);
        Instant now = Instant.now();
        return repository.findActive(tokenHash, now)
            .filter(session -> "ACTIVE".equals(session.status()))
            .map(session -> {
                if (session.lastSeenAt().plus(properties.sessionTouchInterval()).isBefore(now)) {
                    repository.touch(tokenHash, now);
                }
                return OptionalLong.of(session.userId());
            })
            .orElseGet(OptionalLong::empty);
    }

    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            repository.revoke(hash(rawToken), Instant.now());
        }
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CreatedSession(String token, Instant expiresAt) {
    }
}
