package com.qinglian.fitness.auth;

import com.qinglian.fitness.mapper.SessionMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class SessionRepository {

    private final SessionMapper sessionMapper;

    public SessionRepository(SessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    public void create(long userId, String tokenHash, Instant expiresAt) {
        sessionMapper.create(userId, tokenHash, expiresAt);
    }

    public Optional<SessionUser> findActive(String tokenHash, Instant now) {
        return Optional.ofNullable(sessionMapper.findActive(tokenHash, now));
    }

    public void touch(String tokenHash, Instant now) {
        sessionMapper.touch(tokenHash, now);
    }

    public void revoke(String tokenHash, Instant now) {
        sessionMapper.revoke(tokenHash, now);
    }

    public record SessionUser(long userId, Instant lastSeenAt, String status) {
    }
}
