package com.qinglian.fitness.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DeviceDtos {
    private DeviceDtos() {
    }

    public record DeviceView(long id, String code, String name, String category, boolean connected) {
    }

    public record BoundDevice(
        long id,
        String code,
        String name,
        String category,
        boolean connected,
        String deviceModel,
        String bedType,
        String springConfig,
        String serialNumber,
        LocalDate purchasedOn,
        LocalDateTime boundAt
    ) {
    }

    public record BindRequest(
        @NotBlank(message = "请输入设备编号")
        @Size(max = 64, message = "设备编号不能超过64个字符")
        String serialNumber
    ) {
    }

    public enum BindStatus {
        BOUND,
        NOT_FOUND,
        ALREADY_BOUND
    }

    public record BindResult(BindStatus status, BoundDevice device) {
    }
}
