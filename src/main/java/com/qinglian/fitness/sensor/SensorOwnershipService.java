package com.qinglian.fitness.sensor;

import static com.qinglian.fitness.sensor.SensorService.utc;

import com.qinglian.fitness.mapper.SensorMapper;

import org.springframework.stereotype.Service;

import java.time.Instant;

/** Called inside the account/bed transaction, before its ownership row disappears. */
@Service
public class SensorOwnershipService {
    private final SensorMapper mapper;

    public SensorOwnershipService(SensorMapper mapper) {
        this.mapper = mapper;
    }

    public void releaseBed(long user, long bed) {
        mapper.lockBed(bed);
        for (SensorMapper.BindingRef binding : mapper.findOwnedBindings(bed, user)) {
            mapper.lockSensor(binding.sensorId());
            mapper.unbindActive(binding.id(), utc(Instant.now()));
            mapper.completeSensorSessions(binding.sensorId(), "BED_UNBOUND");
            mapper.deleteSensorChallenges(binding.sensorId());
        }
    }

    public void releaseUser(long user) {
        mapper.lockUser(user);
        for (long bed : mapper.findSelectedBedIds(user)) {
            releaseBed(user, bed);
        }
    }
}
