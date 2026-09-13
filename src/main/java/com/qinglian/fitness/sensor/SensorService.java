package com.qinglian.fitness.sensor;

import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.mapper.SensorMapper;
import com.qinglian.fitness.mapper.SensorTrainingMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SensorService {
    private static final Logger log = LoggerFactory.getLogger(SensorService.class);
    private static final DateTimeFormatter LOG_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(ZoneId.of("Asia/Shanghai"));
    private final SensorMapper mapper;
    private final ObjectMapper json;
    private final SensorTrainingMapper trainingMapper;
    private static final SecureRandom RANDOM = new SecureRandom();

    public SensorService(
            SensorMapper mapper, ObjectMapper json, SensorTrainingMapper trainingMapper) {
        this.mapper = mapper;
        this.json = json;
        this.trainingMapper = trainingMapper;
    }

    private static void committed(String message, Object... args) {
        // Success events must not survive a transaction rollback.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            log.info(message, args);
                        }
                    });
        } else {
            log.info(message, args);
        }
    }

    static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }

    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static String secret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    static Instant instant(Map<String, Object> row, String key) {
        return row.get(key) == null
                ? null
                : ((LocalDateTime) row.get(key)).toInstant(ZoneOffset.UTC);
    }

    static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    static boolean flag(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue() != 0;
    }

    private Map<String, Object> device(String id, boolean lock) {
        validateDeviceId(id);
        Map<String, Object> row = mapper.findDevice(id, lock);
        if (row == null) {
            throw error(HttpStatus.NOT_FOUND, "DEVICE_NOT_REGISTERED", "传感器尚未登记");
        }
        return row;
    }

    private static void validateDeviceId(String id) {
        if (id == null || !id.matches("ARVELLO-[A-F0-9]{12}")) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_DEVICE_ID", "设备编号格式不正确");
        }
    }

    Map<String, Object> ensureDevice(String id) {
        validateDeviceId(id);
        String mac = id.substring(8);
        String code =
                "AVS-" + mac.substring(0, 4) + "-" + mac.substring(4, 8) + "-" + mac.substring(8);
        // First contact registers the permanent ID. This is identification, not device
        // authentication.
        mapper.registerDevice(id, code, utc(Instant.now()));
        Map<String, Object> row = device(id, true);
        if (!"ACTIVE".equals(row.get("status"))) {
            throw error(HttpStatus.FORBIDDEN, "DEVICE_DISABLED", "设备已停用");
        }
        return row;
    }

    private long ownedBed(long user, String sn) {
        Long bed = mapper.findOwnedBedId(sn, user);
        if (bed == null) {
            throw error(HttpStatus.NOT_FOUND, "BED_NOT_OWNED", "核心床未绑定到当前账号");
        }
        return bed;
    }

    @Transactional
    public Map<String, Object> register(String deviceId) {
        Map<String, Object> sensor = ensureDevice(deviceId);
        return Map.of(
                "deviceId",
                deviceId,
                "deviceCode",
                sensor.get("device_code"),
                "qrContent",
                "ARVELLO:DEVICE:" + sensor.get("device_code"));
    }

    @Transactional
    public Map<String, Object> challenge(long user, SensorDtos.Claim body) {
        long bed = ownedBed(user, body.bedSn());
        Map<String, Object> sensor = ensureDevice(body.deviceId());
        if (!body.deviceCode().equals(sensor.get("device_code"))) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_DEVICE_ID", "传感器编号不一致");
        }
        long id = number(sensor, "id");
        if (mapper.findConflictingBindingId(id, bed) != null) {
            throw error(HttpStatus.CONFLICT, "ALREADY_BOUND", "传感器或核心床已有有效绑定");
        }
        mapper.deleteUserChallenges(user, id);
        String token = secret();
        mapper.createChallenge(
                new SensorMapper.BindingChallenge(
                        hash(token), id, bed, user, utc(Instant.now().plusSeconds(120))));
        committed(
                "SENSOR_BIND_REQUEST deviceId={} bedId={} userId={} expiresInSeconds=120",
                body.deviceId(),
                bed,
                user);
        return Map.of("challengeId", token, "expiresIn", 120);
    }

    @Transactional
    public void confirm(String id, String challengeId) {
        Map<String, Object> sensor = device(id, true);
        if (!"ACTIVE".equals(sensor.get("status"))) {
            throw error(HttpStatus.FORBIDDEN, "DEVICE_DISABLED", "设备已停用");
        }
        int changed =
                mapper.confirmChallenge(
                        hash(challengeId),
                        number(sensor, "id"),
                        utc(Instant.now()),
                        utc(Instant.now()));
        if (changed == 0) {
            throw error(HttpStatus.CONFLICT, "CLAIM_EXPIRED", "绑定请求已失效");
        }
        committed("SENSOR_BIND_CONFIRMED deviceId={}", id);
    }

    @Transactional
    public Map<String, Object> bind(long user, String challengeId) {
        // Match the existing account binding lock order: user, bed, sensor.
        mapper.lockUser(user);
        Map<String, Object> claim = mapper.findUserChallenge(hash(challengeId), user);
        if (claim == null) {
            throw error(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "绑定请求不存在");
        }
        long bed = number(claim, "bed_id"), sensor = number(claim, "sensor_id");
        mapper.lockBed(bed);
        mapper.lockSensor(sensor);
        claim = mapper.lockChallenge(hash(challengeId));
        if (claim == null
                || claim.get("used_at") != null
                || !instant(claim, "expires_at").isAfter(Instant.now())) {
            throw error(HttpStatus.CONFLICT, "CLAIM_EXPIRED", "绑定请求已失效，请重新连接");
        }
        if (claim.get("confirmed_at") == null) {
            throw error(HttpStatus.CONFLICT, "CLAIM_PENDING", "正在等待传感器确认");
        }
        if (mapper.findSelectedUserId(user, bed) == null) {
            throw error(HttpStatus.FORBIDDEN, "BED_NOT_OWNED", "核心床已不属于当前账号");
        }
        try {
            mapper.createBinding(sensor, bed, user, utc(Instant.now()));
        } catch (DuplicateKeyException e) {
            throw error(HttpStatus.CONFLICT, "ALREADY_BOUND", "传感器或核心床已被绑定");
        }
        mapper.markChallengeUsed(hash(challengeId), utc(Instant.now()));
        committed("SENSOR_BOUND sensorId={} bedId={} userId={}", sensor, bed, user);
        return Map.of("ok", true, "status", "BOUND");
    }

    static void validate(SensorDtos.Reading r) {
        if ((r.motionAxisG() != null && !finite(r.motionAxisG()))
                || (r.activityG() != null && !finite(r.activityG()))
                || (r.accelG() != null && !vector(r.accelG()))
                || (r.gyroDps() != null && !vector(r.gyroDps()))) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", "传感器数值必须有限，三轴数据必须包含三个数值");
        }
        if (Boolean.TRUE.equals(r.sensorOk())) {
            if (r.moving() == null
                    || !Set.of("X", "Y", "Z").contains(r.motionAxis() == null ? "" : r.motionAxis())
                    || !finite(r.motionAxisG())
                    || !finite(r.activityG())
                    || !vector(r.accelG())
                    || !vector(r.gyroDps())) {
                throw error(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", "正常传感器必须上传完整运动数据");
            }
        } else if (r.moving() != null) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", "传感器异常时 moving 必须为 null");
        }
        if (Boolean.TRUE.equals(r.standby()) && Boolean.TRUE.equals(r.moving())) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", "待机时不能同时为运动状态");
        }
    }

    private static boolean finite(Double n) {
        return n != null && Double.isFinite(n);
    }

    private static boolean vector(List<Double> values) {
        return values != null
                && values.size() == 3
                && values.stream().allMatch(SensorService::finite);
    }

    @Transactional
    public Map<String, Object> receive(String id, SensorDtos.Reading r) {
        validateDeviceId(id);
        if (!id.equals(r.deviceId())) {
            throw error(HttpStatus.FORBIDDEN, "DEVICE_ID_MISMATCH", "设备编号不一致");
        }
        validate(r);
        Map<String, Object> sensor = ensureDevice(id);
        long sensorId = number(sensor, "id");
        Instant now = Instant.now();
        LocalDateTime at = utc(now);
        Map<String, Object> previous = mapper.findLatestReading(sensorId);
        boolean sameBoot = previous != null && r.bootId().equals(previous.get("boot_id"));
        if (sameBoot && number(previous, "schema_version") != 3) {
            throw error(HttpStatus.CONFLICT, "PROTOCOL_CHANGED", "切换协议版本时必须更新 bootId");
        }
        if (sameBoot && r.sequence() <= number(previous, "sequence_number")) {
            log.debug(
                    "SENSOR_DUPLICATE deviceId={} bootId={} sequence={} latestSequence={}",
                    id,
                    r.bootId(),
                    r.sequence(),
                    previous.get("sequence_number"));
            return Map.of(
                    "ok",
                    true,
                    "duplicate",
                    true,
                    "deviceId",
                    id,
                    "receivedAt",
                    instant(previous, "received_at"));
        }
        if (!sameBoot) {
            if (mapper.findBoot(sensorId, r.bootId()) != null) {
                throw error(HttpStatus.CONFLICT, "OLD_BOOT", "拒绝旧开机周期的数据");
            }
            mapper.createBoot(sensorId, r.bootId(), at);
            committed(
                    "SENSOR_BOOT deviceId={} bootId={} firstReport={} baselineCount={}",
                    id,
                    r.bootId(),
                    previous == null,
                    r.repetitionCount());
        }
        if (sameBoot && r.repetitionCount() < number(previous, "repetition_count")) {
            throw error(HttpStatus.CONFLICT, "COUNTER_RESET", "清零计数时必须更新 bootId");
        }
        int expired = mapper.expireSessions(sensorId, 3, utc(now.minus(TrainingWindow.PAUSE)));
        if (expired > 0) {
            committed("SENSOR_TRAINING_ENDED deviceId={} reason=IDLE endedAt=last_motion_at", id);
        }
        Map<String, Object> binding = mapper.findCurrentOwnership(sensorId);
        Map<String, Object> session = mapper.lockActiveSession(sensorId);
        if (session != null
                && (number(session, "schema_version") != 3
                        || !sameBoot
                        || binding == null
                        || number(binding, "user_id") != number(session, "user_id"))) {
            close(number(session, "id"), "INTERRUPTED");
            session = null;
        }
        boolean motion = Boolean.TRUE.equals(r.sensorOk()) && Boolean.TRUE.equals(r.moving());
        long baseline = sameBoot ? number(previous, "repetition_count") : r.repetitionCount();
        if (binding != null && session == null && motion) {
            mapper.createLegacySession(
                    new SensorMapper.LegacySession(
                            sensorId,
                            number(binding, "bed_id"),
                            number(binding, "user_id"),
                            r.bootId(),
                            at,
                            baseline,
                            r.repetitionCount()));
            committed(
                    "SENSOR_TRAINING_STARTED deviceId={} bedId={} userId={} startedAt={}"
                            + " startCount={}",
                    id,
                    binding.get("bed_id"),
                    binding.get("user_id"),
                    LOG_TIME.format(now),
                    baseline);
        } else if (session != null) {
            mapper.updateLegacySession(
                    number(session, "id"),
                    r.repetitionCount(),
                    motion ? at : utc(instant(session, "last_motion_at")));
            if (r.standby()) {
                close(number(session, "id"), "STANDBY");
            }
        }
        mapper.upsertLegacyReading(
                new SensorMapper.LegacyReading(
                        sensorId,
                        r.bootId(),
                        r.sequence(),
                        r.repetitionCount(),
                        r.moving(),
                        r.standby(),
                        r.sensorOk(),
                        json.writeValueAsString(r),
                        at));
        mapper.touchDevice(sensorId, at);
        committed(
                "SENSOR_READING deviceId={} bootId={} sequence={} moving={} standby={} sensorOk={}"
                        + " totalCount={} bound={} receivedAt={}",
                id,
                r.bootId(),
                r.sequence(),
                r.moving(),
                r.standby(),
                r.sensorOk(),
                r.repetitionCount(),
                binding != null,
                LOG_TIME.format(now));
        return Map.of("ok", true, "deviceId", id, "receivedAt", now);
    }

    private void close(long session, String reason) {
        int changed = mapper.completeSession(session, reason);
        if (changed > 0) {
            committed(
                    "SENSOR_TRAINING_ENDED sessionId={} reason={} endedAt=last_motion_at",
                    session,
                    reason);
        }
    }

    public void expire() {
        int expired =
                mapper.expireSessions(null, 3, utc(Instant.now().minus(TrainingWindow.PAUSE)));
        if (expired > 0) {
            committed(
                    "SENSOR_TRAINING_EXPIRED sessions={} idleSeconds=180 endedAt=last_motion_at",
                    expired);
        }
        // V4 owns its five-minute idle boundary; a summary can still finalize the same row later.
        mapper.expireSessions(null, 4, utc(Instant.now().minusSeconds(300)));
        mapper.deleteExpiredChallenges(utc(Instant.now().minusSeconds(3600)));
    }

    static Map<String, Object> sessionView(Map<String, Object> row) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", number(row, "id"));
        v.put("startedAt", instant(row, "started_at"));
        v.put("lastMotionAt", instant(row, "last_motion_at"));
        v.put("endedAt", row.get("ended_at") == null ? null : instant(row, "ended_at"));
        v.put("status", row.get("status"));
        v.put("endReason", row.get("end_reason"));
        v.put("durationMs", sessionDuration(row));
        v.put("estimatedCalories", row.get("estimated_calories"));
        v.put("calorieWeightKg", row.get("calorie_weight_kg"));
        v.put("calorieWeightDefaulted", flag(row, "calorie_weight_defaulted"));
        v.put("schemaVersion", row.get("schema_version"));
        v.put("deviceSessionId", row.get("device_session_id"));
        v.put("activeDurationMs", row.get("active_duration_ms"));
        v.put("trainingState", row.get("training_state"));
        v.put("timeValid", flag(row, "time_valid"));
        v.put("timeQuality", row.get("time_quality"));
        v.put("ownershipStatus", row.get("ownership_status"));
        v.put("averagePeriodMs", row.get("average_period_ms"));
        v.put("minPeriodMs", row.get("min_period_ms"));
        v.put("maxPeriodMs", row.get("max_period_ms"));
        v.put("summaryReceived", flag(row, "summary_received"));
        v.put(
                "repetitionCount",
                Math.max(0, number(row, "end_count") - number(row, "start_count")));
        return v;
    }

    static long sessionDuration(Map<String, Object> row) {
        if (row.get("schema_version") instanceof Number schema && schema.intValue() == 4) {
            return row.get("active_duration_ms") == null
                    ? 0
                    : Math.max(0, number(row, "active_duration_ms"));
        }
        return TrainingWindow.elapsedMs(instant(row, "started_at"), instant(row, "last_motion_at"));
    }

    public Map<String, Object> latest(long user, String sn) {
        long bed = ownedBed(user, sn);
        Map<String, Object> binding = mapper.findBedBinding(bed, user);
        if (binding == null) {
            return Map.of("ok", true, "bound", false, "bedSn", sn);
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ok", true);
        v.put("bound", true);
        v.put("bedSn", sn);
        v.put("serialNumber", binding.get("serial_number"));
        v.put("model", binding.get("model"));
        v.put("firmwareVersion", binding.get("firmware_version"));
        v.put("bindingId", number(binding, "id"));
        v.put("deviceId", binding.get("device_id"));
        v.put("deviceCode", binding.get("device_code"));
        long sensor = number(binding, "sensor_id");
        Map<String, Object> reading = mapper.findLatestReading(sensor);
        v.put("state", "WAITING");
        v.put("online", false);
        v.put("receivedAt", null);
        if (reading != null) {
            boolean online =
                    instant(reading, "received_at").isAfter(Instant.now().minusSeconds(90));
            v.put("online", online);
            v.put("receivedAt", instant(reading, "received_at"));
            var payload = json.readTree(reading.get("payload_json").toString());
            for (String key :
                    List.of(
                            "schemaVersion",
                            "trainingState",
                            "sessionId",
                            "repetitionCount",
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
                            "sensorOk",
                            "cachedSessionCount",
                            "timeValid")) {
                if (payload.has(key)) {
                    v.put(key, payload.get(key));
                }
            }
            v.put(
                    "state",
                    flag(reading, "standby")
                            ? "STANDBY"
                            : !online
                                    ? "OFFLINE"
                                    : !flag(reading, "sensor_ok")
                                            ? "ERROR"
                                            : "paused".equals(reading.get("training_state"))
                                                    ? "PAUSED"
                                                    : flag(reading, "moving") ? "MOVING" : "STILL");
        }
        Map<String, Object> session = mapper.findLatestVisibleSession(sensor, bed, user);
        v.put("session", session == null ? null : sessionView(session));
        return v;
    }

    public Map<String, Object> history(long user, String sn, Long before) {
        long bed = ownedBed(user, sn);
        List<Map<String, Object>> rows =
                mapper.findBedHistory(bed, user, before == null ? Long.MAX_VALUE : before);
        boolean more = rows.size() > 20;
        List<Map<String, Object>> items =
                rows.stream().limit(20).map(SensorService::sessionView).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("nextCursor", more ? items.getLast().get("id") : null);
        return result;
    }

    public Map<String, Object> userHistory(long user, Long before) {
        var rows = mapper.findUserHistory(user, before == null ? Long.MAX_VALUE : before);
        var items =
                rows.stream()
                        .limit(20)
                        .map(
                                row -> {
                                    var item = sessionView(row);
                                    item.put("bedSn", row.get("bed_sn"));
                                    item.put("deviceName", row.get("device_name"));
                                    return item;
                                })
                        .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("nextCursor", rows.size() > 20 ? items.getLast().get("id") : null);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> trainingStats(long user) {
        return trainingStats(user, LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    Map<String, Object> trainingStats(long user, LocalDate today) {
        var profile = trainingMapper.findCalorieProfile(user);
        if (profile == null) {
            throw error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录状态无效或已过期");
        }
        LocalDateTime dayStart = utc(today.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        LocalDateTime dayEnd =
                utc(today.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        SensorTrainingMapper.TrainingTotals totals =
                trainingMapper.findTotals(user, dayStart, dayEnd);
        Set<LocalDate> days = new HashSet<>();
        for (SensorTrainingMapper.TrainingDateRange range :
                trainingMapper.findTrainingDateRanges(user)) {
            for (LocalDate day = range.firstDay();
                    !day.isAfter(range.lastDay());
                    day = day.plusDays(1)) {
                days.add(day);
            }
        }
        LocalDate day = today;
        if (!days.contains(day)) {
            day = day.minusDays(1);
        }
        int consecutive = 0;
        while (days.contains(day)) {
            consecutive++;
            day = day.minusDays(1);
        }
        long duration = totals.trainingDurationMs();
        long todayDuration = totals.todayTrainingDurationMs();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completedCount", totals.completedCount());
        result.put("trainingDurationMs", duration);
        result.put("trainingMinutes", duration / 60000);
        result.put("todayTrainingDurationMs", todayDuration);
        result.put("todayTrainingMinutes", todayDuration / 60000);
        result.put("totalEstimatedCalories", totals.totalEstimatedCalories());
        result.put("todayEstimatedCalories", totals.todayEstimatedCalories());
        result.put("calorieWeightKg", profile.weightKg());
        result.put("calorieWeightDefaulted", profile.weightDefaulted());
        result.put("trainingDays", days.size());
        result.put("consecutiveDays", consecutive);
        result.put("repetitionCount", totals.repetitionCount());
        return result;
    }

    @Transactional
    public void unbind(long user, long bindingId) {
        mapper.lockUser(user);
        Map<String, Object> b = mapper.findActiveBinding(bindingId);
        if (b == null) {
            throw error(HttpStatus.NOT_FOUND, "BINDING_NOT_FOUND", "绑定不存在");
        }
        mapper.lockBed(number(b, "bed_id"));
        mapper.lockSensor(number(b, "sensor_id"));
        if (mapper.findSelectedUserId(user, number(b, "bed_id")) == null) {
            throw error(HttpStatus.FORBIDDEN, "BED_NOT_OWNED", "无权解绑这台核心床的传感器");
        }
        mapper.unbind(bindingId, utc(Instant.now()));
        mapper.completeSensorSessions(number(b, "sensor_id"), "UNBOUND");
        mapper.deleteSensorChallenges(number(b, "sensor_id"));
        committed(
                "SENSOR_UNBOUND bindingId={} sensorId={} bedId={} userId={}",
                bindingId,
                b.get("sensor_id"),
                b.get("bed_id"),
                user);
    }
}
