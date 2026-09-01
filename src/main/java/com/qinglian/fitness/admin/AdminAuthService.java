package com.qinglian.fitness.admin;

import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.config.AdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AdminAuthService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private final AdminAuthRepository repository;
    private final AdminProperties properties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminAuthService(AdminAuthRepository repository, AdminProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = normalizedUsername();
        if (repository.findByUsername(username).isPresent()) return;
        if (properties.password() == null || properties.password().isBlank()) {
            log.warn("Admin account was not created because ADMIN_PASSWORD is empty");
            return;
        }
        repository.createAccount(username, passwordEncoder.encode(properties.password()));
        log.info("Created initial admin account: {}", username);
    }

    public LoginResult login(String username, String password, String ipAddress) {
        AdminAuthRepository.AdminAccount account = repository.findByUsername(normalize(username))
            .filter(item -> "ACTIVE".equals(item.status()))
            .filter(item -> password != null && passwordEncoder.matches(password, item.passwordHash()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_FAILED", "账号或密码不正确"));

        String rawToken = randomToken();
        Instant expiresAt = Instant.now().plus(sessionTtl());
        repository.createSession(account.id(), hash(rawToken), expiresAt);
        repository.recordLogin(account.id(), Instant.now());
        repository.audit(account.id(), "LOGIN", "SESSION", null, "管理员登录后台", ipAddress);
        return new LoginResult(rawToken, expiresAt, account.username());
    }

    public Optional<VerifiedAdmin> verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        String tokenHash = hash(rawToken);
        Instant now = Instant.now();
        return repository.findActiveSession(tokenHash, now).map(session -> {
            if (session.lastSeenAt().plus(Duration.ofMinutes(5)).isBefore(now)) {
                repository.touch(tokenHash, now);
            }
            return new VerifiedAdmin(session.adminId(), session.username());
        });
    }

    public void logout(long adminId, String rawToken, String ipAddress) {
        if (rawToken != null && !rawToken.isBlank()) repository.revoke(hash(rawToken), Instant.now());
        repository.audit(adminId, "LOGOUT", "SESSION", null, "管理员退出后台", ipAddress);
    }

    public void audit(long adminId, String action, String targetType, Long targetId, String summary, String ip) {
        repository.audit(adminId, action, targetType, targetId, summary, ip);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizedUsername() {
        String username = normalize(properties.username());
        return username.isBlank() ? "admin" : username;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private Duration sessionTtl() {
        return properties.sessionTtl() == null ? Duration.ofHours(12) : properties.sessionTtl();
    }

    public record LoginResult(String token, Instant expiresAt, String username) {
    }

    public record VerifiedAdmin(long id, String username) {
    }
}
