package com.qinglian.fitness.device;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class DeviceDtos {
    private DeviceDtos() {
    }

    public record DeviceView(long id, String deviceModel) {
    }

    public record BoundDevice(
        long id,
        String deviceModel,
        String serialNumber,
        LocalDateTime boundAt
    ) {
    }

    public record BindRequest(
        @Size(max = 64, message = "设备编号不能超过64个字符") String serialNumber,
        @Size(max = 64, message = "二维码令牌不能超过64个字符") String qrToken
    ) {
    }

    public enum BindStatus {
        BOUND,
        NOT_FOUND,
        ALREADY_BOUND,
        INVALID
    }

    public record BindResult(BindStatus status, BoundDevice device) {
    }
}
