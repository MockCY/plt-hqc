package com.qinglian.fitness.sensor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.util.List;

/** V4 summaries deliberately do not inherit the cumulative telemetry counter contract. */
public final class SensorV4Dtos {
    private SensorV4Dtos() {}
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record Registration(
        @NotBlank @Pattern(regexp="ARVELLO-[A-F0-9]{12}") String deviceId,
        @NotBlank @Size(max=64) String serialNumber,
        @NotBlank @Size(max=64) String model,
        @NotBlank @Size(max=32) String firmwareVersion
    ) {}
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record Telemetry(
        @NotNull @Min(4) @Max(4) Integer schemaVersion,
        @NotBlank @Pattern(regexp="telemetry") String recordType,
        @NotBlank @Pattern(regexp="ARVELLO-[A-F0-9]{12}") String deviceId,
        @NotBlank @Size(max=64) String serialNumber,
        @NotBlank @Size(max=64) String model,
        @NotBlank @Size(max=32) String firmwareVersion,
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{8,36}") String bootId,
        @NotNull @Min(0) Long sequence,
        @NotNull @Min(0) @Max(3153600000000L) Long uptimeMs,
        @NotNull Boolean sensorOk, Boolean moving, @NotNull Boolean standby,
        @NotBlank @Pattern(regexp="continuous_cycle") String countType,
        @NotNull @Min(0) Long repetitionCount,
        String motionAxis, Double motionAxisG, List<Double> accelG, List<Double> gyroDps, Double activityG,
        @NotBlank @Pattern(regexp="idle|active|paused") String trainingState,
        @NotNull @Pattern(regexp="[A-Za-z0-9-]{0,96}") String sessionId,
        @NotNull @Min(0) @Max(3153600000000L) Long activeDurationMs,
        @NotNull @Min(0) Long sessionRepetitionCount,
        @NotNull @Min(0) Long averagePeriodMs,
        @NotNull @Min(0) Long minPeriodMs,
        @NotNull @Min(0) Long maxPeriodMs,
        @NotNull Boolean timeValid,
        @NotNull @Min(0) Long sessionStartedAt,
        @NotNull @Min(0) Long sessionLastMotionAt,
        @NotNull @Min(0) @Max(3153600000000L) Long startUptimeMs,
        @NotNull @Min(0) @Max(3153600000000L) Long endUptimeMs,
        @Min(0) Long faultMask, @Min(0) Integer cachedSessionCount,
        Boolean batteryAvailable, @Min(0) @Max(100) Integer batteryPercent,
        @DecimalMin("0.0") @DecimalMax("20.0") Double batteryVoltage,
        Boolean charging, Boolean batteryFull
    ) {
        SensorDtos.Reading legacyMotion() {
            return new SensorDtos.Reading(3,deviceId,bootId,sequence,uptimeMs,sensorOk,moving,standby,
                countType,repetitionCount,motionAxis,motionAxisG,accelG,gyroDps,activityG);
        }
    }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record Summary(
        @NotNull @Min(4) @Max(4) Integer schemaVersion,
        @NotBlank @Pattern(regexp="training_summary") String recordType,
        @NotBlank @Pattern(regexp="ARVELLO-[A-F0-9]{12}") String deviceId,
        @NotBlank @Size(max=64) String serialNumber,
        @NotBlank @Size(max=64) String model,
        @NotBlank @Size(max=32) String firmwareVersion,
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{8,36}") String bootId,
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{8,96}") String sessionId,
        @NotNull Boolean timeValid,
        @NotNull @Min(0) Long startTime,
        @NotNull @Min(0) Long endTime,
        @NotNull @Min(0) @Max(3153600000000L) Long startUptimeMs,
        @NotNull @Min(0) @Max(3153600000000L) Long endUptimeMs,
        @NotNull @Min(0) @Max(3153600000000L) Long activeDurationMs,
        @NotNull @Min(0) Long repetitionCount,
        @NotNull @Min(0) Long averagePeriodMs,
        @NotNull @Min(0) Long minPeriodMs,
        @NotNull @Min(0) Long maxPeriodMs,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,32}") String endReason,
        // Provenance retained when firmware rescues a cached V0.9 summary with incomplete clocks.
        Boolean uptimeEstimated, Long legacyStartTime, Long legacyEndTime
    ) {}
}
