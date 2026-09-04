package com.qinglian.fitness.device;

import com.qinglian.fitness.device.DeviceDtos.BindResult;
import com.qinglian.fitness.device.DeviceDtos.BindStatus;
import com.qinglian.fitness.device.DeviceDtos.BoundDevice;
import com.qinglian.fitness.device.DeviceDtos.DeviceView;
import com.qinglian.fitness.mapper.DeviceMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class DeviceRepository {
    private final DeviceMapper mapper;

    public DeviceRepository(DeviceMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<BoundDevice> current(long userId) {
        return Optional.ofNullable(mapper.findCurrent(userId));
    }

    @Transactional
    public BindResult bind(long userId, String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return new BindResult(BindStatus.INVALID, null);
        }
        DeviceView device = mapper.findBySerialNumber(serialNumber.trim());
        if (device == null) return new BindResult(BindStatus.NOT_FOUND, null);

        Long bindingUserId = mapper.findBindingUserId(device.id());
        if (bindingUserId != null && bindingUserId != userId) {
            return new BindResult(BindStatus.ALREADY_BOUND, null);
        }

        try {
            if (bindingUserId == null && mapper.updateSelection(userId, device.id()) == 0) {
                mapper.createSelection(userId, device.id());
            }
        } catch (DataIntegrityViolationException exception) {
            return new BindResult(BindStatus.ALREADY_BOUND, null);
        }
        return new BindResult(BindStatus.BOUND, mapper.findCurrent(userId));
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
        return mapper.deleteSelection(userId) > 0;
    }
}
