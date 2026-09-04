package com.qinglian.fitness.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class DeviceDtos {
    private DeviceDtos() {
    }

    public record DeviceView(long id, String deviceModel) {
    }

    public record BoundDevice(
        long id,
        String deviceName,
        String deviceModel,
        String brand,
        String deviceSource,
        String serialNumber,
        LocalDateTime boundAt
    ) {
    }

    public record ThirdPartyDeviceRequest(
        @NotBlank(message = "请输入设备名称") @Size(max = 100, message = "设备名称不能超过100个字符") String deviceName,
        @NotBlank(message = "请输入设备型号") @Size(max = 100, message = "设备型号不能超过100个字符") String deviceModel
    ) {
    }

    public record BindRequest(
        @NotBlank(message = "请输入设备 SN 码")
        @Size(max = 64, message = "设备 SN 码不能超过64个字符")
        @Pattern(regexp = "[A-Za-z0-9-]+", message = "设备 SN 码只能包含字母、数字或连字符") String serialNumber
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
