package com.qinglian.fitness.device;

import com.qinglian.fitness.device.DeviceDtos.BindResult;
import com.qinglian.fitness.device.DeviceDtos.BindStatus;
import com.qinglian.fitness.device.DeviceDtos.BoundDevice;
import com.qinglian.fitness.device.DeviceDtos.DeviceView;
import com.qinglian.fitness.mapper.DeviceMapper;
import com.qinglian.fitness.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public class DeviceRepository {
    private final DeviceMapper mapper;
    private final com.qinglian.fitness.sensor.SensorOwnershipService sensors;

    public DeviceRepository(DeviceMapper mapper,com.qinglian.fitness.sensor.SensorOwnershipService sensors) {
        this.mapper = mapper; this.sensors=sensors;
    }

    public Optional<BoundDevice> current(long userId) {
        return Optional.ofNullable(mapper.findCurrent(userId));
    }

    public List<BoundDevice> list(long userId) {
        return mapper.findAllBound(userId);
    }

    @Transactional
    public BindResult bind(long userId, String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return new BindResult(BindStatus.INVALID, null);
        }
        mapper.lockUser(userId);
        DeviceView device = mapper.findBySerialNumber(serialNumber.trim());
        if (device == null) return new BindResult(BindStatus.NOT_FOUND, null);

        // Serialize claims from different users before checking device ownership.
        if (mapper.lockDevice(device.id()) == null) return new BindResult(BindStatus.NOT_FOUND, null);

        Long bindingUserId = mapper.findBindingUserId(device.id());
        if (bindingUserId != null && bindingUserId != userId) {
            return new BindResult(BindStatus.ALREADY_BOUND, null);
        }

        if (bindingUserId == null) mapper.createSelection(userId, device.id());
        BoundDevice bound = mapper.findBound(userId, device.id());
        if (bindingUserId == null) {
            mapper.recordBound(device.id(), bound.boundAt());
        }
        return new BindResult(BindStatus.BOUND, bound);
    }

    @Transactional
    public BoundDevice createAndBindThirdParty(long userId, String deviceName, String deviceModel) {
        String serialNumber = "TP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        mapper.insertThirdPartyDevice(serialNumber, UUID.randomUUID().toString().replace("-", ""),
            deviceName.trim(), deviceModel.trim());
        BindResult result = bind(userId, serialNumber);
        if (result.status() != BindStatus.BOUND || result.device() == null) {
            throw new IllegalStateException("第三方设备创建成功但绑定失败");
        }
        return result.device();
    }

    @Transactional
    public boolean unbind(long userId) {
        mapper.lockUser(userId);
        List<BoundDevice> devices = mapper.findAllBound(userId);
        if (devices.isEmpty()) return false;
        if (devices.size() > 1) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_ID_REQUIRED", "已绑定多台设备，请选择要解绑的设备");
        }
        return unbind(userId, devices.getFirst().id());
    }

    @Transactional
    public boolean unbind(long userId, long deviceId) {
        mapper.lockUser(userId);
        mapper.lockDevice(deviceId);
        BoundDevice previous = mapper.findBound(userId, deviceId);
        if (previous == null) return false;
        sensors.releaseBed(userId,deviceId);
        if (mapper.deleteSelection(userId, deviceId) == 0) return false;
        mapper.recordUnbound(previous.id(), previous.boundAt());
        return true;
    }
}
