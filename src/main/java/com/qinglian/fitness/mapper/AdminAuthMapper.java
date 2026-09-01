package com.qinglian.fitness.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface AdminAuthMapper {

    AdminAccountRow findByUsername(@Param("username") String username);

    int createAccount(@Param("username") String username, @Param("passwordHash") String passwordHash);

    int createSession(
        @Param("adminId") long adminId,
        @Param("tokenHash") String tokenHash,
        @Param("expiresAt") Instant expiresAt
    );

    AdminSessionRow findActiveSession(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    int touch(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    int revoke(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    int recordLogin(@Param("adminId") long adminId, @Param("now") Instant now);

    int insertAudit(
        @Param("adminId") long adminId,
        @Param("action") String action,
        @Param("targetType") String targetType,
        @Param("targetId") Long targetId,
        @Param("summary") String summary,
        @Param("ipAddress") String ipAddress
    );

    record AdminAccountRow(long id, String username, String passwordHash, String status) {
    }

    record AdminSessionRow(long adminId, String username, Instant lastSeenAt) {
    }
}
