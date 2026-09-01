package com.qinglian.fitness.device;

import com.qinglian.fitness.device.DeviceDtos.BindResult;
import com.qinglian.fitness.device.DeviceDtos.BindStatus;
import com.qinglian.fitness.device.DeviceDtos.BoundDevice;
import com.qinglian.fitness.device.DeviceDtos.DeviceView;
import com.qinglian.fitness.mapper.DeviceMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class DeviceRepository {
    private final DeviceMapper mapper;

    public DeviceRepository(DeviceMapper mapper) {
        this.mapper = mapper;
    }

    public List<DeviceView> findActive() {
        return mapper.findActive();
    }

    public Optional<BoundDevice> current(long userId) {
        return Optional.ofNullable(mapper.findCurrent(userId));
    }

    @Transactional
    public BindResult bind(long userId, String serialNumber, String qrToken) {
        DeviceView device;
        if (qrToken != null && !qrToken.isBlank()) {
            device = mapper.findByQrToken(qrToken.trim());
        } else if (serialNumber != null && !serialNumber.isBlank()) {
            device = mapper.findBySerialNumber(serialNumber.trim());
        } else {
            return new BindResult(BindStatus.INVALID, null);
        }
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
    public boolean unbind(long userId) {
        return mapper.deleteSelection(userId) > 0;
    }
}
