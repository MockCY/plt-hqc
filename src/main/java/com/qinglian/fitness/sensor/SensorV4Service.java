package com.qinglian.fitness.sensor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static com.qinglian.fitness.sensor.SensorService.*;

@Service
public class SensorV4Service {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final SensorService legacy;
    public SensorV4Service(JdbcTemplate db,ObjectMapper json,SensorService legacy) {
        this.db=db; this.json=json; this.legacy=legacy;
    }
    private Map<String,Object> one(String sql,Object... args) {
        var rows=db.queryForList(sql,args); return rows.isEmpty()?null:rows.getFirst();
    }
    private void identity(String header,String deviceId) {
        if (header==null || !header.equals(deviceId))
            throw error(HttpStatus.FORBIDDEN,"DEVICE_ID_MISMATCH","设备编号不一致");
    }
    private Map<String,Object> metadata(String deviceId,String serial,String model,String firmware) {
        return metadata(deviceId,serial,model,firmware,true);
    }
    private Map<String,Object> metadata(String deviceId,String serial,String model,String firmware,boolean current) {
        var sensor=legacy.ensureDevice(deviceId);
        if (sensor.get("serial_number")!=null && !serial.equals(sensor.get("serial_number")))
            throw error(HttpStatus.CONFLICT,"DEVICE_SERIAL_MISMATCH","同一设备编号不能更换传感器序列号");
        db.update(current?"update sensor_devices set serial_number=?,model=?,firmware_version=? where id=?":
            "update sensor_devices set serial_number=?,model=coalesce(model,?),firmware_version=coalesce(firmware_version,?) where id=?",
            serial,model,firmware,number(sensor,"id"));
        return sensor;
    }
    @Transactional
    public Map<String,Object> register(String header,SensorV4Dtos.Registration r) {
        identity(header,r.deviceId());
        var sensor=metadata(r.deviceId(),r.serialNumber(),r.model(),r.firmwareVersion());
        return Map.of("ok",true,"deviceId",r.deviceId(),"deviceCode",sensor.get("device_code"),"schemaVersion",4);
    }
    private static void invalid(String message) { throw error(HttpStatus.BAD_REQUEST,"INVALID_PAYLOAD",message); }
    static void times(boolean valid,long start,long end,long startUp,long endUp,long active) {
        if (endUp<startUp || active>endUp-startUp) invalid("有效运动时长不能超过会话时间跨度");
        if (valid) {
            if (start<1577836800L || end<start || end>Instant.now().plusSeconds(60).getEpochSecond())
                invalid("训练时间无效或在未来");
            // Epoch seconds are rounded; tolerate their subsecond loss.
            if (Math.abs((end-start)*1000L-(endUp-startUp))>2000L)
                invalid("训练时间与设备运行时长不一致");
        } else if (start!=0 || end!=0) invalid("未校时的训练必须使用零时间戳");
    }
    static void periods(long average,long min,long max) {
        if ((average==0 || min==0 || max==0) ? (average!=0 || min!=0 || max!=0) : (min>average || average>max))
            invalid("往返节奏范围不正确");
    }
    static void validate(SensorV4Dtos.Telemetry r) {
        SensorService.validate(r.legacyMotion());
        times(r.timeValid(),r.sessionStartedAt(),r.sessionLastMotionAt(),r.startUptimeMs(),r.endUptimeMs(),r.activeDurationMs());
        periods(r.averagePeriodMs(),r.minPeriodMs(),r.maxPeriodMs());
        if (r.endUptimeMs()>r.uptimeMs() || r.sessionRepetitionCount()>r.repetitionCount()) invalid("会话数值超出累计数据");
        if ("idle".equals(r.trainingState())) {
            if (!r.sessionId().isEmpty() || r.activeDurationMs()!=0 || r.sessionRepetitionCount()!=0 || r.startUptimeMs()!=0 || r.endUptimeMs()!=0 || r.timeValid())
                invalid("空闲设备不能包含进行中的训练");
        } else if (r.sessionId().length()<8) invalid("训练状态必须包含会话编号");
        if (r.batteryVoltage()!=null && !Double.isFinite(r.batteryVoltage())) invalid("电池电压必须有限");
        if (Boolean.FALSE.equals(r.batteryAvailable()) && (r.batteryPercent()!=null || r.batteryVoltage()!=null || r.charging()!=null || r.batteryFull()!=null))
            invalid("未配置电池时不能上传电池测量值");
    }
    private record Times(LocalDateTime start,LocalDateTime end,String quality) {}
    private Times telemetryTimes(SensorV4Dtos.Telemetry r,Instant now) {
        if (r.timeValid()) return new Times(utc(Instant.ofEpochSecond(r.sessionStartedAt())),utc(Instant.ofEpochSecond(r.sessionLastMotionAt())),"DEVICE");
        // Live reports can establish an estimated clock for this session. Historical summaries alone cannot.
        return new Times(utc(now.minusMillis(r.uptimeMs()-r.startUptimeMs())),utc(now.minusMillis(r.uptimeMs()-r.endUptimeMs())),"ESTIMATED");
    }
    private Map<String,Object> historicalBinding(long sensor,Times t) {
        if (t.start()==null || t.end()==null) return null;
        var rows=db.queryForList("select b.* from sensor_device_bindings b join users u on u.id=b.user_id where b.sensor_id=? and b.bound_at<=? and (b.unbound_at is null or b.unbound_at>=?) order by b.bound_at desc limit 2",
            sensor,t.start(),t.end());
        // Ambiguous overlapping history must not allocate another person's workout.
        if (rows.size()!=1) return null;
        var binding=rows.getFirst();
        if (binding.get("unbound_at")==null && one("select user_id from user_device_selections where device_id=? and user_id=? and selected_at<=?",
            binding.get("bed_id"),binding.get("user_id"),t.start())==null) return null;
        return binding;
    }
    private void closeOthers(long sensor,String sessionId,String reason) {
        db.update("update sensor_workout_sessions set status='COMPLETED',training_state='idle',ended_at=last_motion_at,end_reason=? where sensor_id=? and status='ACTIVE' and (device_session_id is null or device_session_id<>?)",
            reason,sensor,sessionId);
    }
    @Transactional
    public Map<String,Object> telemetry(String header,SensorV4Dtos.Telemetry r) {
        identity(header,r.deviceId()); validate(r);
        var sensor=metadata(r.deviceId(),r.serialNumber(),r.model(),r.firmwareVersion());
        long sid=number(sensor,"id"); Instant now=Instant.now(); LocalDateTime at=utc(now);
        var previous=one("select * from sensor_latest_readings where sensor_id=?",sid);
        boolean sameBoot=previous!=null && r.bootId().equals(previous.get("boot_id"));
        if (sameBoot && number(previous,"schema_version")!=4)
            throw error(HttpStatus.CONFLICT,"PROTOCOL_CHANGED","切换协议版本时必须更新 bootId");
        if (sameBoot && r.sequence()<=number(previous,"sequence_number"))
            return Map.of("ok",true,"duplicate",true,"deviceId",r.deviceId(),"receivedAt",instant(previous,"received_at"));
        if (!sameBoot) {
            if (one("select sensor_id from sensor_boots where sensor_id=? and boot_id=?",sid,r.bootId())!=null)
                throw error(HttpStatus.CONFLICT,"OLD_BOOT","拒绝旧开机周期的实时数据");
            db.update("insert into sensor_boots(sensor_id,boot_id,first_seen_at) values(?,?,?)",sid,r.bootId(),at);
            closeOthers(sid,"","INTERRUPTED");
        } else if (r.repetitionCount()<number(previous,"repetition_count"))
            throw error(HttpStatus.CONFLICT,"COUNTER_RESET","清零计数时必须更新 bootId");
        if (!r.sessionId().isEmpty()) {
            Times t=telemetryTimes(r,now);
            var session=one("select * from sensor_workout_sessions where sensor_id=? and device_session_id=? for update",sid,r.sessionId());
            if (session!=null && !r.bootId().equals(session.get("boot_id")))
                throw error(HttpStatus.CONFLICT,"SESSION_ID_REUSED","会话编号不能跨开机周期复用");
            if (session==null || !flag(session,"summary_received")) {
                if (session!=null && (r.sessionRepetitionCount()<number(session,"end_count") || r.activeDurationMs()<number(session,"active_duration_ms")))
                    throw error(HttpStatus.CONFLICT,"SESSION_COUNTER_RESET","同一训练的累计数值不能减少");
                closeOthers(sid,r.sessionId(),"INTERRUPTED");
                // Preserve an initial estimate until device wall time becomes available.
                if (session!=null && !r.timeValid() && session.get("started_at")!=null) {
                    LocalDateTime start=(LocalDateTime)session.get("started_at");
                    t=new Times(start,start.plus(Duration.ofMillis(r.endUptimeMs()-r.startUptimeMs())),session.get("time_quality").toString());
                }
                var binding=historicalBinding(sid,t);
                // Recheck the whole interval: a continuing session can cross a bed ownership change.
                Object bed=binding==null?null:binding.get("bed_id");
                Object user=binding==null?null:binding.get("user_id");
                Object bindingId=binding==null?null:binding.get("id");
                if (session==null) {
                    db.update("insert into sensor_workout_sessions(sensor_id,bed_id,user_id,binding_id,boot_id,schema_version,device_session_id,started_at,last_motion_at,start_count,end_count,active_duration_ms,training_state,time_valid,time_quality,ownership_status,start_uptime_ms,end_uptime_ms,average_period_ms,min_period_ms,max_period_ms,last_received_at) values(?,?,?,?,?,4,?,?,?,0,?,?,?,?,?,?,?,?,?,?,?,?)",
                        sid,bed,user,bindingId,r.bootId(),r.sessionId(),t.start(),t.end(),r.sessionRepetitionCount(),r.activeDurationMs(),r.trainingState(),r.timeValid(),t.quality(),user==null?"PENDING":"ASSIGNED",r.startUptimeMs(),r.endUptimeMs(),r.averagePeriodMs(),r.minPeriodMs(),r.maxPeriodMs(),at);
                } else {
                    // Closed ownership boundaries may be supplemented, never reopened by late telemetry.
                    String state="ACTIVE".equals(session.get("status"))?r.trainingState():"idle";
                    db.update("update sensor_workout_sessions set bed_id=?,user_id=?,binding_id=?,started_at=?,last_motion_at=?,end_count=?,active_duration_ms=?,training_state=?,time_valid=?,time_quality=?,ownership_status=?,start_uptime_ms=?,end_uptime_ms=?,average_period_ms=?,min_period_ms=?,max_period_ms=?,last_received_at=? where id=?",
                        bed,user,bindingId,t.start(),t.end(),r.sessionRepetitionCount(),r.activeDurationMs(),state,r.timeValid(),t.quality(),user==null?"PENDING":"ASSIGNED",r.startUptimeMs(),r.endUptimeMs(),r.averagePeriodMs(),r.minPeriodMs(),r.maxPeriodMs(),at,session.get("id"));
                }
            }
        } else closeOthers(sid,"",r.standby()?"STANDBY":"DEVICE_IDLE");
        db.update("insert into sensor_latest_readings(sensor_id,boot_id,sequence_number,repetition_count,moving,standby,sensor_ok,payload_json,received_at,schema_version,training_state) values(?,?,?,?,?,?,?,?,?,4,?) on duplicate key update boot_id=values(boot_id),sequence_number=values(sequence_number),repetition_count=values(repetition_count),moving=values(moving),standby=values(standby),sensor_ok=values(sensor_ok),payload_json=values(payload_json),received_at=values(received_at),schema_version=4,training_state=values(training_state)",
            sid,r.bootId(),r.sequence(),r.repetitionCount(),r.moving(),r.standby(),r.sensorOk(),json.writeValueAsString(r),at,r.trainingState());
        db.update("update sensor_devices set last_seen_at=? where id=?",at,sid);
        return Map.of("ok",true,"deviceId",r.deviceId(),"receivedAt",now);
    }
    @Transactional
    public Map<String,Object> summary(String header,SensorV4Dtos.Summary r) {
        identity(header,r.deviceId());
        times(r.timeValid(),r.startTime(),r.endTime(),r.startUptimeMs(),r.endUptimeMs(),r.activeDurationMs());
        periods(r.averagePeriodMs(),r.minPeriodMs(),r.maxPeriodMs());
        var sensor=metadata(r.deviceId(),r.serialNumber(),r.model(),r.firmwareVersion(),false);
        long sid=number(sensor,"id"); LocalDateTime at=utc(Instant.now());
        var session=one("select * from sensor_workout_sessions where sensor_id=? and device_session_id=? for update",sid,r.sessionId());
        if (session!=null && !r.bootId().equals(session.get("boot_id")))
            throw error(HttpStatus.CONFLICT,"SESSION_ID_REUSED","会话编号不能跨开机周期复用");
        if (session!=null && flag(session,"summary_received")) {
            var saved=json.readValue(session.get("summary_payload_json").toString(),SensorV4Dtos.Summary.class);
            if (!sameSummary(saved,r)) throw error(HttpStatus.CONFLICT,"SUMMARY_CONFLICT","已接收的训练摘要内容不一致");
            // Permit a previously unknown clock to be repaired without ever adding another workout.
            if (flag(session,"time_valid") || !r.timeValid()) return ack(r.sessionId(),true);
        }
        Times t=r.timeValid()?new Times(utc(Instant.ofEpochSecond(r.startTime())),utc(Instant.ofEpochSecond(r.endTime())),"DEVICE"):
            session!=null && session.get("started_at")!=null?new Times((LocalDateTime)session.get("started_at"),((LocalDateTime)session.get("started_at")).plus(Duration.ofMillis(r.endUptimeMs()-r.startUptimeMs())),session.get("time_quality").toString()):new Times(null,null,"UNKNOWN");
        var binding=historicalBinding(sid,t);
        Object bed=binding==null?null:binding.get("bed_id"),user=binding==null?null:binding.get("user_id"),bindingId=binding==null?null:binding.get("id");
        if (session==null) {
            db.update("insert into sensor_workout_sessions(sensor_id,bed_id,user_id,binding_id,boot_id,schema_version,device_session_id,started_at,last_motion_at,ended_at,start_count,end_count,status,training_state,end_reason,active_duration_ms,time_valid,time_quality,ownership_status,start_uptime_ms,end_uptime_ms,average_period_ms,min_period_ms,max_period_ms,summary_received,summary_payload_json,last_received_at) values(?,?,?,?,?,4,?,?,?,?,0,?,'COMPLETED','idle',?,?,?,?,?,?,?,?,?,?,1,?,?)",
                sid,bed,user,bindingId,r.bootId(),r.sessionId(),t.start(),t.end(),t.end(),r.repetitionCount(),r.endReason(),r.activeDurationMs(),r.timeValid(),t.quality(),user==null?"PENDING":"ASSIGNED",r.startUptimeMs(),r.endUptimeMs(),r.averagePeriodMs(),r.minPeriodMs(),r.maxPeriodMs(),json.writeValueAsString(r),at);
        } else {
            db.update("update sensor_workout_sessions set bed_id=?,user_id=?,binding_id=?,started_at=?,last_motion_at=?,ended_at=?,start_count=0,end_count=?,status='COMPLETED',training_state='idle',end_reason=?,active_duration_ms=?,time_valid=?,time_quality=?,ownership_status=?,start_uptime_ms=?,end_uptime_ms=?,average_period_ms=?,min_period_ms=?,max_period_ms=?,summary_received=1,summary_payload_json=?,last_received_at=? where id=?",
                bed,user,bindingId,t.start(),t.end(),t.end(),r.repetitionCount(),r.endReason(),r.activeDurationMs(),r.timeValid(),t.quality(),user==null?"PENDING":"ASSIGNED",r.startUptimeMs(),r.endUptimeMs(),r.averagePeriodMs(),r.minPeriodMs(),r.maxPeriodMs(),json.writeValueAsString(r),at,session.get("id"));
        }
        return ack(r.sessionId(),false);
    }
    private static boolean sameSummary(SensorV4Dtos.Summary a,SensorV4Dtos.Summary b) {
        return a.deviceId().equals(b.deviceId()) && a.bootId().equals(b.bootId()) && a.serialNumber().equals(b.serialNumber()) &&
            a.startUptimeMs().equals(b.startUptimeMs()) && a.endUptimeMs().equals(b.endUptimeMs()) && a.activeDurationMs().equals(b.activeDurationMs()) &&
            a.repetitionCount().equals(b.repetitionCount()) && a.averagePeriodMs().equals(b.averagePeriodMs()) && a.minPeriodMs().equals(b.minPeriodMs()) &&
            a.maxPeriodMs().equals(b.maxPeriodMs()) && a.endReason().equals(b.endReason()) &&
            (!a.timeValid() || !b.timeValid() || a.startTime().equals(b.startTime()) && a.endTime().equals(b.endTime()));
    }
    private static Map<String,Object> ack(String sessionId,boolean duplicate) {
        // @Transactional interceptor commits before the controller serializes this acknowledgement.
        return Map.of("ok",true,"accepted",true,"sessionId",sessionId,"duplicate",duplicate);
    }
}
