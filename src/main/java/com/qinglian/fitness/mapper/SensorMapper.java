package com.qinglian.fitness.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SensorMapper {
    Map<String, Object> findDevice(@Param("deviceId") String deviceId, @Param("lock") boolean lock);

    int registerDevice(
            @Param("deviceId") String deviceId,
            @Param("deviceCode") String deviceCode,
            @Param("createdAt") LocalDateTime createdAt);

    Long findOwnedBedId(@Param("serialNumber") String serialNumber, @Param("userId") long userId);

    Long findConflictingBindingId(@Param("sensorId") long sensorId, @Param("bedId") long bedId);

    int deleteUserChallenges(@Param("userId") long userId, @Param("sensorId") long sensorId);

    int createChallenge(BindingChallenge challenge);

    int confirmChallenge(
            @Param("tokenHash") String tokenHash,
            @Param("sensorId") long sensorId,
            @Param("confirmedAt") LocalDateTime confirmedAt,
            @Param("validAfter") LocalDateTime validAfter);

    Long lockUser(@Param("userId") long userId);

    Long lockBed(@Param("bedId") long bedId);

    Long lockSensor(@Param("sensorId") long sensorId);

    Map<String, Object> findUserChallenge(
            @Param("tokenHash") String tokenHash, @Param("userId") long userId);

    Map<String, Object> lockChallenge(@Param("tokenHash") String tokenHash);

    Long findSelectedUserId(@Param("userId") long userId, @Param("bedId") long bedId);

    int createBinding(
            @Param("sensorId") long sensorId,
            @Param("bedId") long bedId,
            @Param("userId") long userId,
            @Param("boundAt") LocalDateTime boundAt);

    int markChallengeUsed(
            @Param("tokenHash") String tokenHash, @Param("usedAt") LocalDateTime usedAt);

    Map<String, Object> findLatestReading(@Param("sensorId") long sensorId);

    Long findBoot(@Param("sensorId") long sensorId, @Param("bootId") String bootId);

    int createBoot(
            @Param("sensorId") long sensorId,
            @Param("bootId") String bootId,
            @Param("firstSeenAt") LocalDateTime firstSeenAt);

    int expireSessions(
            @Param("sensorId") Long sensorId,
            @Param("schemaVersion") int schemaVersion,
            @Param("cutoff") LocalDateTime cutoff);

    Map<String, Object> findCurrentOwnership(@Param("sensorId") long sensorId);

    Map<String, Object> lockActiveSession(@Param("sensorId") long sensorId);

    int createLegacySession(LegacySession session);

    int updateLegacySession(
            @Param("sessionId") long sessionId,
            @Param("endCount") long endCount,
            @Param("lastMotionAt") LocalDateTime lastMotionAt);

    int upsertLegacyReading(LegacyReading reading);

    int touchDevice(
            @Param("sensorId") long sensorId, @Param("lastSeenAt") LocalDateTime lastSeenAt);

    int completeSession(@Param("sessionId") long sessionId, @Param("reason") String reason);

    int deleteExpiredChallenges(@Param("cutoff") LocalDateTime cutoff);

    Map<String, Object> findBedBinding(@Param("bedId") long bedId, @Param("userId") long userId);

    Map<String, Object> findLatestVisibleSession(
            @Param("sensorId") long sensorId,
            @Param("bedId") long bedId,
            @Param("userId") long userId);

    List<Map<String, Object>> findBedHistory(
            @Param("bedId") long bedId, @Param("userId") long userId, @Param("before") long before);

    List<Map<String, Object>> findUserHistory(
            @Param("userId") long userId, @Param("before") long before);

    Map<String, Object> findActiveBinding(@Param("bindingId") long bindingId);

    int unbind(@Param("bindingId") long bindingId, @Param("unboundAt") LocalDateTime unboundAt);

    int unbindActive(
            @Param("bindingId") long bindingId, @Param("unboundAt") LocalDateTime unboundAt);

    int completeSensorSessions(@Param("sensorId") long sensorId, @Param("reason") String reason);

    int deleteSensorChallenges(@Param("sensorId") long sensorId);

    List<BindingRef> findOwnedBindings(@Param("bedId") long bedId, @Param("userId") long userId);

    List<Long> findSelectedBedIds(@Param("userId") long userId);

    record BindingChallenge(
            String tokenHash, long sensorId, long bedId, long userId, LocalDateTime expiresAt) {}

    record LegacySession(
            long sensorId,
            long bedId,
            long userId,
            String bootId,
            LocalDateTime startedAt,
            long startCount,
            long endCount) {}

    record LegacyReading(
            long sensorId,
            String bootId,
            long sequenceNumber,
            long repetitionCount,
            Boolean moving,
            Boolean standby,
            Boolean sensorOk,
            String payloadJson,
            LocalDateTime receivedAt) {}

    record BindingRef(long id, long sensorId) {}
}
