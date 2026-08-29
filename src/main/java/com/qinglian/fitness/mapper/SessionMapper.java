package com.qinglian.fitness.mapper;

import com.qinglian.fitness.auth.SessionRepository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface SessionMapper {

    int create(
        @Param("userId") long userId,
        @Param("tokenHash") String tokenHash,
        @Param("expiresAt") Instant expiresAt
    );

    SessionRepository.SessionUser findActive(
        @Param("tokenHash") String tokenHash,
        @Param("now") Instant now
    );

    int touch(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    int revoke(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
