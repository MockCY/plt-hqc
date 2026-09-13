package com.qinglian.fitness.mapper;

import com.qinglian.fitness.sensor.SensorV4Dtos;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SensorV4Mapper {

    int updateMetadata(DeviceMetadata command);

    List<HistoricalBinding> findHistoricalBindings(
            @Param("sensorId") long sensorId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("endedAt") LocalDateTime endedAt);

    boolean hasSelectionBefore(
            @Param("bedId") long bedId,
            @Param("userId") long userId,
            @Param("startedAt") LocalDateTime startedAt);

    int closeOtherSessions(
            @Param("sensorId") long sensorId,
            @Param("sessionId") String sessionId,
            @Param("reason") String reason);

    LatestReading findLatestReading(@Param("sensorId") long sensorId);

    boolean bootExists(@Param("sensorId") long sensorId, @Param("bootId") String bootId);

    int insertBoot(
            @Param("sensorId") long sensorId,
            @Param("bootId") String bootId,
            @Param("firstSeenAt") LocalDateTime firstSeenAt);

    WorkoutSession lockSession(
            @Param("sensorId") long sensorId, @Param("sessionId") String sessionId);

    int insertTelemetrySession(TelemetrySession command);

    int updateTelemetrySession(TelemetrySession command);

    int upsertLatestReading(LatestReadingUpdate command);

    int updateLastSeen(
            @Param("sensorId") long sensorId, @Param("lastSeenAt") LocalDateTime lastSeenAt);

    int insertSummarySession(SummarySession command);

    int updateSummarySession(SummarySession command);

    record DeviceMetadata(
            long sensorId,
            String serialNumber,
            String model,
            String firmwareVersion,
            boolean current) {}

    record HistoricalBinding(long id, long bedId, long userId, LocalDateTime unboundAt) {}

    record LatestReading(
            String bootId,
            int schemaVersion,
            long sequenceNumber,
            long repetitionCount,
            LocalDateTime receivedAt) {}

    record WorkoutSession(
            long id,
            String bootId,
            boolean summaryReceived,
            long endCount,
            Long activeDurationMs,
            LocalDateTime startedAt,
            String timeQuality,
            String status,
            boolean timeValid,
            String summaryPayloadJson) {}

    record Ownership(Long bedId, Long userId, Long bindingId, String status) {}

    record TelemetrySession(
            Long id,
            long sensorId,
            Ownership ownership,
            SensorV4Dtos.Telemetry telemetry,
            LocalDateTime startedAt,
            LocalDateTime lastMotionAt,
            String timeQuality,
            String trainingState,
            LocalDateTime receivedAt) {}

    record LatestReadingUpdate(
            long sensorId,
            SensorV4Dtos.Telemetry telemetry,
            String payloadJson,
            LocalDateTime receivedAt) {}

    record SummarySession(
            Long id,
            long sensorId,
            Ownership ownership,
            SensorV4Dtos.Summary summary,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            String timeQuality,
            String payloadJson,
            LocalDateTime receivedAt) {}
}
