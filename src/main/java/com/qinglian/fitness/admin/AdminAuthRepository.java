package com.qinglian.fitness.admin;

import com.qinglian.fitness.mapper.AdminAuthMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class AdminAuthRepository {

    private final AdminAuthMapper mapper;

    public AdminAuthRepository(AdminAuthMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<AdminAccount> findByUsername(String username) {
        return Optional.ofNullable(mapper.findByUsername(username))
            .map(row -> new AdminAccount(row.id(), row.username(), row.passwordHash(), row.status()));
    }

    public void createAccount(String username, String passwordHash) {
        mapper.createAccount(username, passwordHash);
    }

    public void createSession(long adminId, String tokenHash, Instant expiresAt) {
        mapper.createSession(adminId, tokenHash, expiresAt);
    }

    public Optional<AdminSession> findActiveSession(String tokenHash, Instant now) {
        return Optional.ofNullable(mapper.findActiveSession(tokenHash, now))
            .map(row -> new AdminSession(row.adminId(), row.username(), row.lastSeenAt()));
    }

    public void touch(String tokenHash, Instant now) {
        mapper.touch(tokenHash, now);
    }

    public void revoke(String tokenHash, Instant now) {
        mapper.revoke(tokenHash, now);
    }

    public void recordLogin(long adminId, Instant now) {
        mapper.recordLogin(adminId, now);
    }

    public void audit(long adminId, String action, String targetType, Long targetId, String summary, String ip) {
        mapper.insertAudit(adminId, action, targetType, targetId, summary, ip);
    }

    public record AdminAccount(long id, String username, String passwordHash, String status) {
    }

    public record AdminSession(long adminId, String username, Instant lastSeenAt) {
    }
}
