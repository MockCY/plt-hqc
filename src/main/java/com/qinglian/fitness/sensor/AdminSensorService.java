package com.qinglian.fitness.sensor;

import static com.qinglian.fitness.sensor.SensorService.error;
import static com.qinglian.fitness.sensor.SensorService.number;
import static com.qinglian.fitness.sensor.SensorService.sessionDuration;
import static com.qinglian.fitness.sensor.SensorService.utc;

import com.qinglian.fitness.admin.AdminDtos.PageResult;
import com.qinglian.fitness.mapper.AdminSensorMapper;
import com.qinglian.fitness.mapper.AdminSensorMapper.SensorFilter;
import com.qinglian.fitness.mapper.AdminSensorMapper.WorkoutFilter;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AdminSensorService {

    private static final ZoneId TRAINING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> SENSOR_STATES =
            Set.of(
                    "ALL",
                    "WAITING",
                    "MOVING",
                    "STILL",
                    "ERROR",
                    "OFFLINE",
                    "STANDBY",
                    "DISABLED",
                    "PAUSED");
    private static final Set<String> BINDING_STATES = Set.of("ALL", "BOUND", "UNBOUND");
    private static final Set<String> WORKOUT_STATES = Set.of("ALL", "ACTIVE", "COMPLETED");
    private static final Set<String> DEVICE_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final List<String> TELEMETRY_FIELDS =
            List.of(
                    "uptimeMs",
                    "countType",
                    "motionAxis",
                    "motionAxisG",
                    "accelG",
                    "gyroDps",
                    "activityG",
                    "schemaVersion",
                    "trainingState",
                    "sessionId",
                    "sessionRepetitionCount",
                    "activeDurationMs",
                    "averagePeriodMs",
                    "minPeriodMs",
                    "maxPeriodMs",
                    "batteryAvailable",
                    "batteryPercent",
                    "batteryVoltage",
                    "charging",
                    "batteryFull",
                    "faultMask",
                    "cachedSessionCount",
                    "timeValid");
    private static final Map<String, String> FIELDS =
            Map.ofEntries(
                    Map.entry("serial_number", "serialNumber"),
                    Map.entry("firmware_version", "firmwareVersion"),
                    Map.entry("schema_version", "schemaVersion"),
                    Map.entry("device_session_id", "deviceSessionId"),
                    Map.entry("active_duration_ms", "activeDurationMs"),
                    Map.entry("estimated_calories", "estimatedCalories"),
                    Map.entry("calorie_weight_kg", "calorieWeightKg"),
                    Map.entry("calorie_weight_defaulted", "calorieWeightDefaulted"),
                    Map.entry("training_state", "trainingState"),
                    Map.entry("time_valid", "timeValid"),
                    Map.entry("time_quality", "timeQuality"),
                    Map.entry("ownership_status", "ownershipStatus"),
                    Map.entry("average_period_ms", "averagePeriodMs"),
                    Map.entry("min_period_ms", "minPeriodMs"),
                    Map.entry("max_period_ms", "maxPeriodMs"),
                    Map.entry("summary_received", "summaryReceived"),
                    Map.entry("start_uptime_ms", "startUptimeMs"),
                    Map.entry("end_uptime_ms", "endUptimeMs"),
                    Map.entry("last_received_at", "lastReceivedAt"),
                    Map.entry("device_id", "deviceId"),
                    Map.entry("device_code", "deviceCode"),
                    Map.entry("created_at", "createdAt"),
                    Map.entry("last_seen_at", "lastSeenAt"),
                    Map.entry("binding_id", "bindingId"),
                    Map.entry("bed_id", "bedId"),
                    Map.entry("bed_sn", "bedSn"),
                    Map.entry("bound_at", "boundAt"),
                    Map.entry("unbound_at", "unboundAt"),
                    Map.entry("user_id", "userId"),
                    Map.entry("user_name", "userName"),
                    Map.entry("user_phone", "userPhone"),
                    Map.entry("boot_id", "bootId"),
                    Map.entry("sequence_number", "sequence"),
                    Map.entry("repetition_count", "repetitionCount"),
                    Map.entry("sensor_ok", "sensorOk"),
                    Map.entry("received_at", "receivedAt"),
                    Map.entry("sensor_id", "sensorId"),
                    Map.entry("started_at", "startedAt"),
                    Map.entry("last_motion_at", "lastMotionAt"),
                    Map.entry("ended_at", "endedAt"),
                    Map.entry("start_count", "startCount"),
                    Map.entry("end_count", "endCount"),
                    Map.entry("end_reason", "endReason"));
    private static final List<String> SENSOR_NULLABLE_FIELDS =
            List.of(
                    "serial_number",
                    "model",
                    "firmware_version",
                    "last_seen_at",
                    "binding_id",
                    "bed_id",
                    "bound_at",
                    "bed_sn",
                    "user_id",
                    "user_name",
                    "user_phone",
                    "boot_id",
                    "sequence_number",
                    "repetition_count",
                    "moving",
                    "standby",
                    "sensor_ok",
                    "received_at",
                    "schema_version",
                    "training_state");
    private static final List<String> BINDING_NULLABLE_FIELDS =
            List.of("unbound_at", "bed_sn", "user_name", "user_phone");
    private static final List<String> WORKOUT_NULLABLE_FIELDS =
            List.of(
                    "bed_id",
                    "user_id",
                    "started_at",
                    "last_motion_at",
                    "ended_at",
                    "end_reason",
                    "device_session_id",
                    "binding_id",
                    "active_duration_ms",
                    "training_state",
                    "start_uptime_ms",
                    "end_uptime_ms",
                    "average_period_ms",
                    "min_period_ms",
                    "max_period_ms",
                    "last_received_at",
                    "bed_sn",
                    "user_name",
                    "user_phone");

    private final AdminSensorMapper mapper;
    private final ObjectMapper json;

    public AdminSensorService(AdminSensorMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public PageResult<Map<String, Object>> sensors(
            String query,
            String state,
            String binding,
            Long bedId,
            Long userId,
            int page,
            int size) {
        String selectedState = option(state, SENSOR_STATES);
        String selectedBinding = option(binding, BINDING_STATES);
        String pattern = searchPattern(query);
        validateId(bedId);
        validateId(userId);
        validatePaging(page, size);
        SensorFilter filter =
                new SensorFilter(pattern, selectedState, selectedBinding, bedId, userId);
        long count = mapper.countSensors(filter);
        var items =
                mapper.findSensors(filter, size, (page - 1) * size).stream()
                        .map(row -> view(row, SENSOR_NULLABLE_FIELDS))
                        .toList();
        return new PageResult<>(items, count, page, size);
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> row = mapper.findSensor(id);
        if (row == null) {
            throw error(HttpStatus.NOT_FOUND, "SENSOR_NOT_FOUND", "传感器不存在");
        }
        Map<String, Object> result = view(row, SENSOR_NULLABLE_FIELDS);
        String latestPayload = mapper.findLatestPayload(id);
        if (latestPayload != null) {
            var payload = json.readTree(latestPayload);
            Map<String, Object> telemetry = new LinkedHashMap<>();
            // Only expose telemetry; sensor credentials must not enter the administrator UI.
            for (String key : TELEMETRY_FIELDS) {
                if (payload.has(key)) {
                    telemetry.put(key, payload.get(key));
                }
            }
            result.put("telemetry", telemetry);
        }
        return result;
    }

    public PageResult<Map<String, Object>> bindings(long sensorId, int page, int size) {
        detail(sensorId);
        validatePaging(page, size);
        long count = mapper.countBindings(sensorId);
        var items =
                mapper.findBindings(sensorId, size, (page - 1) * size).stream()
                        .map(row -> view(row, BINDING_NULLABLE_FIELDS))
                        .toList();
        return new PageResult<>(items, count, page, size);
    }

    public Map<String, Object> workouts(
            String query,
            String status,
            Long sensorId,
            Long bedId,
            Long userId,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        String selectedStatus = option(status, WORKOUT_STATES);
        String pattern = searchPattern(query);
        validateId(sensorId);
        validateId(bedId);
        validateId(userId);
        if (from != null && to != null && from.isAfter(to)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "开始日期不能晚于结束日期");
        }
        LocalDateTime fromTime =
                from == null ? null : utc(from.atStartOfDay(TRAINING_ZONE).toInstant());
        LocalDateTime untilTime =
                to == null ? null : utc(to.plusDays(1).atStartOfDay(TRAINING_ZONE).toInstant());
        validatePaging(page, size);
        WorkoutFilter filter =
                new WorkoutFilter(
                        pattern, selectedStatus, sensorId, bedId, userId, fromTime, untilTime);
        long count = mapper.countWorkouts(filter);
        var items =
                mapper.findWorkouts(filter, size, (page - 1) * size).stream()
                        .map(this::workoutView)
                        .toList();
        LocalDate today = LocalDate.now(TRAINING_ZONE);
        var summary = mapper.summarizeWorkouts(
                filter,
                utc(today.atStartOfDay(TRAINING_ZONE).toInstant()),
                utc(today.plusDays(1).atStartOfDay(TRAINING_ZONE).toInstant()));
        return Map.of(
                "items", items,
                "total", count,
                "page", page,
                "pageSize", size,
                "summary", summary);
    }

    public Map<String, Object> workout(long id) {
        Map<String, Object> row = mapper.findWorkout(id);
        if (row == null) {
            throw error(HttpStatus.NOT_FOUND, "WORKOUT_NOT_FOUND", "训练记录不存在");
        }
        return workoutView(row);
    }

    @Transactional
    public void unbind(long id, long bindingId) {
        lock(id);
        int changed = mapper.unbind(id, bindingId, utc(Instant.now()));
        if (changed == 0) {
            throw error(HttpStatus.CONFLICT, "BINDING_CHANGED", "绑定已发生变化，请刷新后重试");
        }
        end(id, "ADMIN_UNBOUND");
    }

    @Transactional
    public void status(long id, String status) {
        if (!DEVICE_STATUSES.contains(status)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "传感器状态不正确");
        }
        lock(id);
        mapper.updateStatus(id, status);
        if (status.equals("DISABLED")) {
            end(id, "DISABLED");
        }
    }

    private void lock(long id) {
        if (mapper.lockSensor(id) == null) {
            throw error(HttpStatus.NOT_FOUND, "SENSOR_NOT_FOUND", "传感器不存在");
        }
    }

    private void end(long id, String reason) {
        mapper.endWorkouts(id, reason);
        mapper.deleteBindingChallenges(id);
    }

    private Map<String, Object> view(Map<String, Object> row, List<String> nullableFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach(
                (key, value) -> {
                    Object converted = value;
                    if (value instanceof LocalDateTime time) {
                        converted = time.toInstant(ZoneOffset.UTC);
                    }
                    result.put(FIELDS.getOrDefault(key, key), converted);
                });
        // MyBatis omits null map entries; retain the existing API's explicit nullable fields.
        for (String key : nullableFields) {
            result.putIfAbsent(FIELDS.getOrDefault(key, key), null);
        }
        return result;
    }

    private Map<String, Object> workoutView(Map<String, Object> row) {
        Map<String, Object> result = view(row, WORKOUT_NULLABLE_FIELDS);
        result.put("durationMs", sessionDuration(row));
        result.put("calorieWeightDefaulted", SensorService.flag(row, "calorie_weight_defaulted"));
        result.put(
                "repetitionCount",
                Math.max(0, number(row, "end_count") - number(row, "start_count")));
        return result;
    }

    private void validatePaging(int page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "分页参数不正确");
        }
    }

    private String option(String value, Set<String> options) {
        if (value == null || value.isBlank()) {
            return "ALL";
        }
        if (!options.contains(value)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "筛选条件不正确");
        }
        return value;
    }

    private String searchPattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        if (query.length() > 100) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "搜索内容过长");
        }
        return "%" + query.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private void validateId(Long value) {
        if (value != null && value < 1) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "编号必须为正整数");
        }
    }
}
