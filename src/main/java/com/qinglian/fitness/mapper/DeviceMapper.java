package com.qinglian.fitness.mapper;

import com.qinglian.fitness.device.DeviceDtos.BoundDevice;
import com.qinglian.fitness.device.DeviceDtos.DeviceView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceMapper {
    BoundDevice findCurrent(@Param("userId") long userId);
    DeviceView findBySerialNumber(@Param("serialNumber") String serialNumber);
    Long findBindingUserId(@Param("deviceId") long deviceId);
    int updateSelection(@Param("userId") long userId, @Param("deviceId") long deviceId);
    int createSelection(@Param("userId") long userId, @Param("deviceId") long deviceId);
    int deleteSelection(@Param("userId") long userId);
}
