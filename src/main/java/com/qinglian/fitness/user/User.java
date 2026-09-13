package com.qinglian.fitness.user;

import java.math.BigDecimal;
import java.time.Instant;

public record User(
    long id,
    String openId,
    String unionId,
    String phone,
    String nickname,
    String avatarUrl,
    String status,
    Instant createdAt,
    Instant updatedAt,
    BigDecimal heightCm,
    BigDecimal weightKg
) {
    public User(long id, String openId, String unionId, String phone, String nickname,
                String avatarUrl, String status, Instant createdAt, Instant updatedAt) {
        this(id, openId, unionId, phone, nickname, avatarUrl, status, createdAt, updatedAt, null, null);
    }
}
