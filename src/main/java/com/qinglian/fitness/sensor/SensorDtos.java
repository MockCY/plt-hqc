package com.qinglian.fitness.sensor;

import jakarta.validation.constraints.*;
import java.util.List;

public final class SensorDtos {
    private SensorDtos() {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Reading(
        @NotNull @Min(3) @Max(3) Integer schemaVersion,
        @NotBlank @Pattern(regexp="ARVELLO-[A-F0-9]{12}") String deviceId,
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{8,36}") String bootId,
        @NotNull @Min(0) Long sequence,
        @NotNull @Min(0) Long uptimeMs,
        @NotNull Boolean sensorOk,
        Boolean moving,
        @NotNull Boolean standby,
        @NotBlank @Pattern(regexp="continuous_cycle") String countType,
        @NotNull @Min(0) Long repetitionCount,
        String motionAxis, Double motionAxisG, List<Double> accelG,
        List<Double> gyroDps, Double activityG
    ) {}
    public record Register(
        @NotBlank @Pattern(regexp="ARVELLO-[A-F0-9]{12}") String deviceId
    ) {}
    public record Claim(
        @NotBlank @Size(max=64) String deviceId,
        @NotBlank @Size(max=64) String deviceCode,
        @NotBlank @Size(max=64) String bedSn
    ) {}
    public record Confirm(@NotBlank @Size(max=100) String challengeId) {}
    public record Binding(@NotBlank @Size(max=100) String challengeId) {}
}
