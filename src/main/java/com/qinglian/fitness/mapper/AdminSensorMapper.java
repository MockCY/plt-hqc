package com.qinglian.fitness.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminSensorMapper {

    long countSensors(@Param("filter") SensorFilter filter);

    List<Map<String, Object>> findSensors(
            @Param("filter") SensorFilter filter,
            @Param("limit") int limit,
            @Param("offset") int offset);

    Map<String, Object> findSensor(@Param("id") long id);

    String findLatestPayload(@Param("sensorId") long sensorId);

    long countBindings(@Param("sensorId") long sensorId);

    List<Map<String, Object>> findBindings(
            @Param("sensorId") long sensorId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countWorkouts(@Param("filter") WorkoutFilter filter);

    List<Map<String, Object>> findWorkouts(
            @Param("filter") WorkoutFilter filter,
            @Param("limit") int limit,
            @Param("offset") int offset);

    Map<String, Object> summarizeWorkouts(
            @Param("filter") WorkoutFilter filter,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd);

    Map<String, Object> findWorkout(@Param("id") long id);

    Long lockSensor(@Param("id") long id);

    int unbind(
            @Param("sensorId") long sensorId,
            @Param("bindingId") long bindingId,
            @Param("unboundAt") LocalDateTime unboundAt);

    int updateStatus(@Param("id") long id, @Param("status") String status);

    int endWorkouts(@Param("sensorId") long sensorId, @Param("reason") String reason);

    int deleteBindingChallenges(@Param("sensorId") long sensorId);

    record SensorFilter(String pattern, String state, String binding, Long bedId, Long userId) {}

    record WorkoutFilter(
            String pattern,
            String status,
            Long sensorId,
            Long bedId,
            Long userId,
            LocalDateTime from,
            LocalDateTime until) {}
}
