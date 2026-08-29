package com.qinglian.fitness.user;

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
    Instant updatedAt
) {
}
