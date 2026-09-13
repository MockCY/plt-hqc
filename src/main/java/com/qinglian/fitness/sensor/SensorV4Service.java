package com.qinglian.fitness.sensor;

import static com.qinglian.fitness.sensor.SensorService.error;
import static com.qinglian.fitness.sensor.SensorService.number;
import static com.qinglian.fitness.sensor.SensorService.utc;

import com.qinglian.fitness.mapper.SensorV4Mapper;
import com.qinglian.fitness.mapper.SensorV4Mapper.DeviceMetadata;
import com.qinglian.fitness.mapper.SensorV4Mapper.HistoricalBinding;
import com.qinglian.fitness.mapper.SensorV4Mapper.LatestReadingUpdate;
import com.qinglian.fitness.mapper.SensorV4Mapper.Ownership;
import com.qinglian.fitness.mapper.SensorV4Mapper.SummarySession;
import com.qinglian.fitness.mapper.SensorV4Mapper.TelemetrySession;
import com.qinglian.fitness.mapper.SensorV4Mapper.WorkoutSession;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class SensorV4Service {

    private final SensorV4Mapper mapper;
    private final ObjectMapper json;
    private final SensorService legacy;

    public SensorV4Service(SensorV4Mapper mapper, ObjectMapper json, SensorService legacy) {
        this.mapper = mapper;
        this.json = json;
        this.legacy = legacy;
    }

    private void identity(String header, String deviceId) {
        if (header == null || !header.equals(deviceId)) {
            throw error(HttpStatus.FORBIDDEN, "DEVICE_ID_MISMATCH", "设备编号不一致");
        }
    }

    private Map<String, Object> metadata(
            String deviceId, String serial, String model, String firmware) {
        return metadata(deviceId, serial, model, firmware, true);
    }

    private Map<String, Object> metadata(
            String deviceId, String serial, String model, String firmware, boolean current) {
        var sensor = legacy.ensureDevice(deviceId);
        if (sensor.get("serial_number") != null && !serial.equals(sensor.get("serial_number"))) {
            throw error(HttpStatus.CONFLICT, "DEVICE_SERIAL_MISMATCH", "同一设备编号不能更换传感器序列号");
        }
        mapper.updateMetadata(
                new DeviceMetadata(number(sensor, "id"), serial, model, firmware, current));
        return sensor;
    }

    @Transactional
    public Map<String, Object> register(String header, SensorV4Dtos.Registration request) {
        identity(header, request.deviceId());
        var sensor =
                metadata(
                        request.deviceId(),
                        request.serialNumber(),
                        request.model(),
                        request.firmwareVersion());
        return Map.of(
                "ok",
                true,
                "deviceId",
                request.deviceId(),
                "deviceCode",
                sensor.get("device_code"),
                "schemaVersion",
                4);
    }

    private static void invalid(String message) {
        throw error(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", message);
    }

    static void times(boolean valid, long start, long end, long startUp, long endUp, long active) {
        if (endUp < startUp || active > endUp - startUp) {
            invalid("有效运动时长不能超过会话时间跨度");
        }
        if (valid) {
            if (start < 1577836800L
                    || end < start
                    || end > Instant.now().plusSeconds(60).getEpochSecond()) {
                invalid("训练时间无效或在未来");
            }
            // Epoch seconds are rounded; tolerate their subsecond loss.
            if (Math.abs((end - start) * 1000L - (endUp - startUp)) > 2000L) {
                invalid("训练时间与设备运行时长不一致");
            }
        } else if (start != 0 || end != 0) {
            invalid("未校时的训练必须使用零时间戳");
        }
    }

    static void periods(long average, long min, long max) {
        boolean hasZero = average == 0 || min == 0 || max == 0;
        boolean invalidRange =
                hasZero ? average != 0 || min != 0 || max != 0 : min > average || average > max;
        if (invalidRange) {
            invalid("往返节奏范围不正确");
        }
    }

    static void validate(SensorV4Dtos.Telemetry request) {
        SensorService.validate(request.legacyMotion());
        times(
                request.timeValid(),
                request.sessionStartedAt(),
                request.sessionLastMotionAt(),
                request.startUptimeMs(),
                request.endUptimeMs(),
                request.activeDurationMs());
        periods(request.averagePeriodMs(), request.minPeriodMs(), request.maxPeriodMs());
        if (request.endUptimeMs() > request.uptimeMs()
                || request.sessionRepetitionCount() > request.repetitionCount()) {
            invalid("会话数值超出累计数据");
        }
        if ("idle".equals(request.trainingState())) {
            if (!request.sessionId().isEmpty()
                    || request.activeDurationMs() != 0
                    || request.sessionRepetitionCount() != 0
                    || request.startUptimeMs() != 0
                    || request.endUptimeMs() != 0
                    || request.timeValid()) {
                invalid("空闲设备不能包含进行中的训练");
            }
        } else if (request.sessionId().length() < 8) {
            invalid("训练状态必须包含会话编号");
        }
        if (request.batteryVoltage() != null && !Double.isFinite(request.batteryVoltage())) {
            invalid("电池电压必须有限");
        }
        if (Boolean.FALSE.equals(request.batteryAvailable())
                && (request.batteryPercent() != null
                        || request.batteryVoltage() != null
                        || request.charging() != null
                        || request.batteryFull() != null)) {
            invalid("未配置电池时不能上传电池测量值");
        }
    }

    private record Times(LocalDateTime start, LocalDateTime end, String quality) {}

    private Times telemetryTimes(SensorV4Dtos.Telemetry request, Instant now) {
        if (request.timeValid()) {
            return new Times(
                    utc(Instant.ofEpochSecond(request.sessionStartedAt())),
                    utc(Instant.ofEpochSecond(request.sessionLastMotionAt())),
                    "DEVICE");
        }
        // Live reports can establish an estimated clock for this session. Historical summaries
        // alone cannot.
        return new Times(
                utc(now.minusMillis(request.uptimeMs() - request.startUptimeMs())),
                utc(now.minusMillis(request.uptimeMs() - request.endUptimeMs())),
                "ESTIMATED");
    }

    private HistoricalBinding historicalBinding(long sensorId, Times times) {
        if (times.start() == null || times.end() == null) {
            return null;
        }
        var bindings = mapper.findHistoricalBindings(sensorId, times.start(), times.end());
        // Ambiguous overlapping history must not allocate another person's workout.
        if (bindings.size() != 1) {
            return null;
        }
        var binding = bindings.getFirst();
        if (binding.unboundAt() == null
                && !mapper.hasSelectionBefore(binding.bedId(), binding.userId(), times.start())) {
            return null;
        }
        return binding;
    }

    private Ownership ownership(long sensorId, Times times) {
        var binding = historicalBinding(sensorId, times);
        if (binding == null) {
            return new Ownership(null, null, null, "PENDING");
        }
        return new Ownership(binding.bedId(), binding.userId(), binding.id(), "ASSIGNED");
    }

    @Transactional
    public Map<String, Object> telemetry(String header, SensorV4Dtos.Telemetry request) {
        identity(header, request.deviceId());
        validate(request);
        var sensor =
                metadata(
                        request.deviceId(),
                        request.serialNumber(),
                        request.model(),
                        request.firmwareVersion());
        long sensorId = number(sensor, "id");
        Instant now = Instant.now();
        LocalDateTime receivedAt = utc(now);
        var previous = mapper.findLatestReading(sensorId);
        boolean sameBoot = previous != null && request.bootId().equals(previous.bootId());
        if (sameBoot && previous.schemaVersion() != 4) {
            throw error(HttpStatus.CONFLICT, "PROTOCOL_CHANGED", "切换协议版本时必须更新 bootId");
        }
        if (sameBoot && request.sequence() <= previous.sequenceNumber()) {
            return Map.of(
                    "ok",
                    true,
                    "duplicate",
                    true,
                    "deviceId",
                    request.deviceId(),
                    "receivedAt",
                    previous.receivedAt().toInstant(ZoneOffset.UTC));
        }
        if (!sameBoot) {
            if (mapper.bootExists(sensorId, request.bootId())) {
                throw error(HttpStatus.CONFLICT, "OLD_BOOT", "拒绝旧开机周期的实时数据");
            }
            mapper.insertBoot(sensorId, request.bootId(), receivedAt);
            mapper.closeOtherSessions(sensorId, "", "INTERRUPTED");
        } else if (request.repetitionCount() < previous.repetitionCount()) {
            throw error(HttpStatus.CONFLICT, "COUNTER_RESET", "清零计数时必须更新 bootId");
        }

        if (!request.sessionId().isEmpty()) {
            recordTelemetrySession(sensorId, request, now, receivedAt);
        } else {
            mapper.closeOtherSessions(sensorId, "", request.standby() ? "STANDBY" : "DEVICE_IDLE");
        }
        mapper.upsertLatestReading(
                new LatestReadingUpdate(
                        sensorId, request, json.writeValueAsString(request), receivedAt));
        mapper.updateLastSeen(sensorId, receivedAt);
        return Map.of("ok", true, "deviceId", request.deviceId(), "receivedAt", now);
    }

    private void recordTelemetrySession(
            long sensorId, SensorV4Dtos.Telemetry request, Instant now, LocalDateTime receivedAt) {
        Times times = telemetryTimes(request, now);
        var session = mapper.lockSession(sensorId, request.sessionId());
        if (session != null && !request.bootId().equals(session.bootId())) {
            throw error(HttpStatus.CONFLICT, "SESSION_ID_REUSED", "会话编号不能跨开机周期复用");
        }
        if (session != null && session.summaryReceived()) {
            return;
        }
        if (session != null
                && (request.sessionRepetitionCount() < session.endCount()
                        || request.activeDurationMs() < session.activeDurationMs())) {
            throw error(HttpStatus.CONFLICT, "SESSION_COUNTER_RESET", "同一训练的累计数值不能减少");
        }
        mapper.closeOtherSessions(sensorId, request.sessionId(), "INTERRUPTED");
        // Preserve an initial estimate until device wall time becomes available.
        if (session != null && !request.timeValid() && session.startedAt() != null) {
            LocalDateTime start = session.startedAt();
            times =
                    new Times(
                            start,
                            start.plus(
                                    Duration.ofMillis(
                                            request.endUptimeMs() - request.startUptimeMs())),
                            session.timeQuality());
        }
        // Recheck the whole interval: a continuing session can cross a bed ownership change.
        Ownership ownership = ownership(sensorId, times);
        // Closed ownership boundaries may be supplemented, never reopened by late telemetry.
        String state =
                session == null || "ACTIVE".equals(session.status())
                        ? request.trainingState()
                        : "idle";
        var command =
                new TelemetrySession(
                        session == null ? null : session.id(),
                        sensorId,
                        ownership,
                        request,
                        times.start(),
                        times.end(),
                        times.quality(),
                        state,
                        receivedAt);
        if (session == null) {
            mapper.insertTelemetrySession(command);
        } else {
            mapper.updateTelemetrySession(command);
        }
    }

    @Transactional
    public Map<String, Object> summary(String header, SensorV4Dtos.Summary request) {
        identity(header, request.deviceId());
        times(
                request.timeValid(),
                request.startTime(),
                request.endTime(),
                request.startUptimeMs(),
                request.endUptimeMs(),
                request.activeDurationMs());
        periods(request.averagePeriodMs(), request.minPeriodMs(), request.maxPeriodMs());
        var sensor =
                metadata(
                        request.deviceId(),
                        request.serialNumber(),
                        request.model(),
                        request.firmwareVersion(),
                        false);
        long sensorId = number(sensor, "id");
        LocalDateTime receivedAt = utc(Instant.now());
        var session = mapper.lockSession(sensorId, request.sessionId());
        if (session != null && !request.bootId().equals(session.bootId())) {
            throw error(HttpStatus.CONFLICT, "SESSION_ID_REUSED", "会话编号不能跨开机周期复用");
        }
        if (session != null && session.summaryReceived()) {
            var saved = json.readValue(session.summaryPayloadJson(), SensorV4Dtos.Summary.class);
            if (!sameSummary(saved, request)) {
                throw error(HttpStatus.CONFLICT, "SUMMARY_CONFLICT", "已接收的训练摘要内容不一致");
            }
            // Permit a previously unknown clock to be repaired without ever adding another workout.
            if (session.timeValid() || !request.timeValid()) {
                return ack(request.sessionId(), true);
            }
        }
        Times times = summaryTimes(request, session);
        Ownership ownership = ownership(sensorId, times);
        var command =
                new SummarySession(
                        session == null ? null : session.id(),
                        sensorId,
                        ownership,
                        request,
                        times.start(),
                        times.end(),
                        times.quality(),
                        json.writeValueAsString(request),
                        receivedAt);
        if (session == null) {
            mapper.insertSummarySession(command);
        } else {
            mapper.updateSummarySession(command);
        }
        return ack(request.sessionId(), false);
    }

    private Times summaryTimes(SensorV4Dtos.Summary request, WorkoutSession session) {
        if (request.timeValid()) {
            return new Times(
                    utc(Instant.ofEpochSecond(request.startTime())),
                    utc(Instant.ofEpochSecond(request.endTime())),
                    "DEVICE");
        }
        if (session != null && session.startedAt() != null) {
            LocalDateTime start = session.startedAt();
            return new Times(
                    start,
                    start.plus(Duration.ofMillis(request.endUptimeMs() - request.startUptimeMs())),
                    session.timeQuality());
        }
        return new Times(null, null, "UNKNOWN");
    }

    private static boolean sameSummary(SensorV4Dtos.Summary saved, SensorV4Dtos.Summary incoming) {
        return saved.deviceId().equals(incoming.deviceId())
                && saved.bootId().equals(incoming.bootId())
                && saved.serialNumber().equals(incoming.serialNumber())
                && saved.startUptimeMs().equals(incoming.startUptimeMs())
                && saved.endUptimeMs().equals(incoming.endUptimeMs())
                && saved.activeDurationMs().equals(incoming.activeDurationMs())
                && saved.repetitionCount().equals(incoming.repetitionCount())
                && saved.averagePeriodMs().equals(incoming.averagePeriodMs())
                && saved.minPeriodMs().equals(incoming.minPeriodMs())
                && saved.maxPeriodMs().equals(incoming.maxPeriodMs())
                && saved.endReason().equals(incoming.endReason())
                && (!saved.timeValid()
                        || !incoming.timeValid()
                        || saved.startTime().equals(incoming.startTime())
                                && saved.endTime().equals(incoming.endTime()));
    }

    private static Map<String, Object> ack(String sessionId, boolean duplicate) {
        // @Transactional interceptor commits before the controller serializes this acknowledgement.
        return Map.of("ok", true, "accepted", true, "sessionId", sessionId, "duplicate", duplicate);
    }
}
