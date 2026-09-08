package com.qinglian.fitness.sensor;

import com.qinglian.fitness.common.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.*;

@Service
public class SensorService {
    private static final Logger log = LoggerFactory.getLogger(SensorService.class);
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private static final SecureRandom RANDOM = new SecureRandom();
    public SensorService(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }

    private static void committed(String message, Object... args) {
        // Success events must not survive a transaction rollback.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { log.info(message,args); }
            });
        } else log.info(message,args);
    }

    static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    static String secret() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private Map<String,Object> one(String sql, Object... args) {
        List<Map<String,Object>> rows = db.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    static long number(Map<String,Object> row, String key) { return ((Number)row.get(key)).longValue(); }
    static Instant instant(Map<String,Object> row, String key) { return ((LocalDateTime)row.get(key)).toInstant(ZoneOffset.UTC); }
    static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value,ZoneOffset.UTC); }
    static boolean flag(Map<String,Object> row, String key) {
        Object v = row.get(key); return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue() != 0;
    }
    private Map<String,Object> device(String id, boolean lock) {
        validateDeviceId(id);
        Map<String,Object> row = one("select * from sensor_devices where device_id=?" + (lock ? " for update" : ""), id);
        if (row == null) throw error(HttpStatus.NOT_FOUND,"DEVICE_NOT_REGISTERED","传感器尚未登记");
        return row;
    }
    private static void validateDeviceId(String id) {
        if (id == null || !id.matches("ARVELLO-[A-F0-9]{12}"))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_DEVICE_ID","设备编号格式不正确");
    }
    private Map<String,Object> ensureDevice(String id) {
        validateDeviceId(id);
        String mac = id.substring(8);
        String code = "AVS-" + mac.substring(0,4) + "-" + mac.substring(4,8) + "-" + mac.substring(8);
        // First contact registers the permanent ID. This is identification, not device authentication.
        db.update("insert into sensor_devices(device_id,device_code,created_at) values(?,?,?) on duplicate key update id=id",
            id,code,utc(Instant.now()));
        Map<String,Object> row = device(id,true);
        if (!"ACTIVE".equals(row.get("status"))) throw error(HttpStatus.FORBIDDEN,"DEVICE_DISABLED","设备已停用");
        return row;
    }
    private long ownedBed(long user, String sn) {
        Map<String,Object> bed = one("select d.id from devices d join user_device_selections u on u.device_id=d.id where d.serial_number=? and u.user_id=?", sn,user);
        if (bed == null) throw error(HttpStatus.NOT_FOUND,"BED_NOT_OWNED","核心床未绑定到当前账号");
        return number(bed,"id");
    }

    @Transactional
    public Map<String,Object> register(String deviceId) {
        Map<String,Object> sensor = ensureDevice(deviceId);
        return Map.of("deviceId",deviceId,"deviceCode",sensor.get("device_code"),
            "qrContent","ARVELLO:DEVICE:"+sensor.get("device_code"));
    }

    @Transactional
    public Map<String,Object> challenge(long user, SensorDtos.Claim body) {
        long bed = ownedBed(user, body.bedSn());
        Map<String,Object> sensor = ensureDevice(body.deviceId());
        if (!body.deviceCode().equals(sensor.get("device_code")))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_DEVICE_ID","传感器编号不一致");
        long id = number(sensor,"id");
        if (one("select id from sensor_device_bindings where unbound_at is null and (sensor_id=? or bed_id=?)",id,bed) != null)
            throw error(HttpStatus.CONFLICT,"ALREADY_BOUND","传感器或核心床已有有效绑定");
        db.update("delete from sensor_binding_challenges where user_id=? and sensor_id=?",user,id);
        String token = secret();
        db.update("insert into sensor_binding_challenges(token_hash,sensor_id,bed_id,user_id,expires_at) values(?,?,?,?,?)",
            hash(token),id,bed,user,utc(Instant.now().plusSeconds(120)));
        committed("SENSOR_BIND_REQUEST deviceId={} bedId={} userId={} expiresInSeconds=120",body.deviceId(),bed,user);
        return Map.of("challengeId",token,"expiresIn",120);
    }

    @Transactional
    public void confirm(String id, String challengeId) {
        Map<String,Object> sensor = device(id,true);
        if (!"ACTIVE".equals(sensor.get("status"))) throw error(HttpStatus.FORBIDDEN,"DEVICE_DISABLED","设备已停用");
        int changed = db.update("update sensor_binding_challenges set confirmed_at=? where token_hash=? and sensor_id=? and expires_at>? and used_at is null",
            utc(Instant.now()),hash(challengeId),number(sensor,"id"),utc(Instant.now()));
        if (changed == 0) throw error(HttpStatus.CONFLICT,"CLAIM_EXPIRED","绑定请求已失效");
        committed("SENSOR_BIND_CONFIRMED deviceId={}",id);
    }

    @Transactional
    public Map<String,Object> bind(long user, String challengeId) {
        // Match the existing account binding lock order: user, bed, sensor.
        one("select id from users where id=? for update",user);
        Map<String,Object> claim = one("select * from sensor_binding_challenges where token_hash=? and user_id=?",hash(challengeId),user);
        if (claim == null) throw error(HttpStatus.NOT_FOUND,"CLAIM_NOT_FOUND","绑定请求不存在");
        long bed = number(claim,"bed_id"), sensor = number(claim,"sensor_id");
        one("select id from devices where id=? for update",bed);
        one("select id from sensor_devices where id=? for update",sensor);
        claim = one("select * from sensor_binding_challenges where token_hash=? for update",hash(challengeId));
        if (claim == null || claim.get("used_at") != null || !instant(claim,"expires_at").isAfter(Instant.now()))
            throw error(HttpStatus.CONFLICT,"CLAIM_EXPIRED","绑定请求已失效，请重新连接");
        if (claim.get("confirmed_at") == null) throw error(HttpStatus.CONFLICT,"CLAIM_PENDING","正在等待传感器确认");
        if (one("select user_id from user_device_selections where user_id=? and device_id=?",user,bed) == null)
            throw error(HttpStatus.FORBIDDEN,"BED_NOT_OWNED","核心床已不属于当前账号");
        try { db.update("insert into sensor_device_bindings(sensor_id,bed_id,user_id,bound_at) values(?,?,?,?)",sensor,bed,user,utc(Instant.now())); }
        catch (DuplicateKeyException e) { throw error(HttpStatus.CONFLICT,"ALREADY_BOUND","传感器或核心床已被绑定"); }
        db.update("update sensor_binding_challenges set used_at=? where token_hash=?",utc(Instant.now()),hash(challengeId));
        committed("SENSOR_BOUND sensorId={} bedId={} userId={}",sensor,bed,user);
        return Map.of("ok",true,"status","BOUND");
    }

    static void validate(SensorDtos.Reading r) {
        if ((r.motionAxisG() != null && !finite(r.motionAxisG())) ||
            (r.activityG() != null && !finite(r.activityG())) ||
            (r.accelG() != null && !vector(r.accelG())) || (r.gyroDps() != null && !vector(r.gyroDps())))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_PAYLOAD","传感器数值必须有限，三轴数据必须包含三个数值");
        if (Boolean.TRUE.equals(r.sensorOk())) {
            if (r.moving() == null || !Set.of("X","Y","Z").contains(r.motionAxis() == null ? "" : r.motionAxis()) ||
                !finite(r.motionAxisG()) || !finite(r.activityG()) || !vector(r.accelG()) || !vector(r.gyroDps()))
                throw error(HttpStatus.BAD_REQUEST,"INVALID_PAYLOAD","正常传感器必须上传完整运动数据");
        } else if (r.moving() != null) throw error(HttpStatus.BAD_REQUEST,"INVALID_PAYLOAD","传感器异常时 moving 必须为 null");
        if (Boolean.TRUE.equals(r.standby()) && Boolean.TRUE.equals(r.moving()))
            throw error(HttpStatus.BAD_REQUEST,"INVALID_PAYLOAD","待机时不能同时为运动状态");
    }
    private static boolean finite(Double n) { return n != null && Double.isFinite(n); }
    private static boolean vector(List<Double> values) { return values != null && values.size()==3 && values.stream().allMatch(SensorService::finite); }

    @Transactional
    public Map<String,Object> receive(String id, SensorDtos.Reading r) {
        validateDeviceId(id);
        if (!id.equals(r.deviceId())) throw error(HttpStatus.FORBIDDEN,"DEVICE_ID_MISMATCH","设备编号不一致");
        validate(r);
        Map<String,Object> sensor = ensureDevice(id);
        long sensorId = number(sensor,"id");
        Instant now = Instant.now(); LocalDateTime at = utc(now);
        Map<String,Object> previous = one("select * from sensor_latest_readings where sensor_id=?",sensorId);
        boolean sameBoot = previous != null && r.bootId().equals(previous.get("boot_id"));
        if (sameBoot && r.sequence() <= number(previous,"sequence_number")) {
            log.debug("SENSOR_DUPLICATE deviceId={} bootId={} sequence={} latestSequence={}",id,r.bootId(),r.sequence(),previous.get("sequence_number"));
            return Map.of("ok",true,"duplicate",true,"deviceId",id,"receivedAt",instant(previous,"received_at"));
        }
        if (!sameBoot) {
            if (one("select sensor_id from sensor_boots where sensor_id=? and boot_id=?",sensorId,r.bootId()) != null)
                throw error(HttpStatus.CONFLICT,"OLD_BOOT","拒绝旧开机周期的数据");
            db.update("insert into sensor_boots values(?,?,?)",sensorId,r.bootId(),at);
            committed("SENSOR_BOOT deviceId={} bootId={} firstReport={} baselineCount={}",id,r.bootId(),previous==null,r.repetitionCount());
        }
        if (sameBoot && r.repetitionCount() < number(previous,"repetition_count"))
            throw error(HttpStatus.CONFLICT,"COUNTER_RESET","清零计数时必须更新 bootId");
        int expired = db.update("update sensor_workout_sessions set status='COMPLETED',ended_at=last_motion_at,end_reason='IDLE' where sensor_id=? and status='ACTIVE' and last_motion_at<=?",
            sensorId,utc(now.minus(TrainingWindow.PAUSE)));
        if (expired > 0) committed("SENSOR_TRAINING_ENDED deviceId={} reason=IDLE endedAt=last_motion_at",id);
        Map<String,Object> binding = one("select b.id,b.bed_id,u.user_id from sensor_device_bindings b join user_device_selections u on u.device_id=b.bed_id where b.sensor_id=? and b.unbound_at is null",sensorId);
        Map<String,Object> session = one("select * from sensor_workout_sessions where sensor_id=? and status='ACTIVE' for update",sensorId);
        if (session != null && (!sameBoot || binding == null || number(binding,"user_id") != number(session,"user_id"))) {
            close(number(session,"id"),"INTERRUPTED"); session = null;
        }
        boolean motion = Boolean.TRUE.equals(r.sensorOk()) && Boolean.TRUE.equals(r.moving());
        long baseline = sameBoot ? number(previous,"repetition_count") : r.repetitionCount();
        if (binding != null && session == null && motion) {
            db.update("insert into sensor_workout_sessions(sensor_id,bed_id,user_id,boot_id,started_at,last_motion_at,start_count,end_count) values(?,?,?,?,?,?,?,?)",
                sensorId,number(binding,"bed_id"),number(binding,"user_id"),r.bootId(),at,at,baseline,r.repetitionCount());
            committed("SENSOR_TRAINING_STARTED deviceId={} bedId={} userId={} startedAt={} startCount={}",
                id,binding.get("bed_id"),binding.get("user_id"),now,baseline);
        } else if (session != null) {
            db.update("update sensor_workout_sessions set end_count=?,last_motion_at=? where id=?",
                r.repetitionCount(),motion ? at : utc(instant(session,"last_motion_at")),number(session,"id"));
            if (r.standby()) close(number(session,"id"),"STANDBY");
        }
        db.update("insert into sensor_latest_readings(sensor_id,boot_id,sequence_number,repetition_count,moving,standby,sensor_ok,payload_json,received_at) values(?,?,?,?,?,?,?,?,?) on duplicate key update boot_id=values(boot_id),sequence_number=values(sequence_number),repetition_count=values(repetition_count),moving=values(moving),standby=values(standby),sensor_ok=values(sensor_ok),payload_json=values(payload_json),received_at=values(received_at)",
            sensorId,r.bootId(),r.sequence(),r.repetitionCount(),r.moving(),r.standby(),r.sensorOk(),json.writeValueAsString(r),at);
        db.update("update sensor_devices set last_seen_at=? where id=?",at,sensorId);
        committed("SENSOR_READING deviceId={} bootId={} sequence={} moving={} standby={} sensorOk={} totalCount={} bound={} receivedAt={}",
            id,r.bootId(),r.sequence(),r.moving(),r.standby(),r.sensorOk(),r.repetitionCount(),binding!=null,now);
        return Map.of("ok",true,"deviceId",id,"receivedAt",now);
    }
    private void close(long session, String reason) {
        int changed = db.update("update sensor_workout_sessions set status='COMPLETED',ended_at=last_motion_at,end_reason=? where id=? and status='ACTIVE'",reason,session);
        if (changed > 0) committed("SENSOR_TRAINING_ENDED sessionId={} reason={} endedAt=last_motion_at",session,reason);
    }
    public void expire() {
        int expired = db.update("update sensor_workout_sessions set status='COMPLETED',ended_at=last_motion_at,end_reason='IDLE' where status='ACTIVE' and last_motion_at<=?",utc(Instant.now().minus(TrainingWindow.PAUSE)));
        if (expired > 0) committed("SENSOR_TRAINING_EXPIRED sessions={} idleSeconds=180 endedAt=last_motion_at",expired);
        db.update("delete from sensor_binding_challenges where expires_at<?",utc(Instant.now().minusSeconds(3600)));
    }

    private Map<String,Object> sessionView(Map<String,Object> row) {
        Map<String,Object> v = new LinkedHashMap<>();
        v.put("id",number(row,"id")); v.put("startedAt",instant(row,"started_at"));
        v.put("lastMotionAt",instant(row,"last_motion_at"));
        v.put("endedAt",row.get("ended_at") == null ? null : instant(row,"ended_at"));
        v.put("status",row.get("status")); v.put("endReason",row.get("end_reason"));
        v.put("durationMs",TrainingWindow.elapsedMs(instant(row,"started_at"),instant(row,"last_motion_at")));
        v.put("repetitionCount",Math.max(0,number(row,"end_count")-number(row,"start_count")));
        return v;
    }
    public Map<String,Object> latest(long user, String sn) {
        long bed = ownedBed(user,sn);
        Map<String,Object> binding = one("select b.id,b.sensor_id,s.device_id,s.device_code from sensor_device_bindings b join sensor_devices s on s.id=b.sensor_id where bed_id=? and unbound_at is null",bed);
        if (binding == null) return Map.of("ok",true,"bound",false,"bedSn",sn);
        Map<String,Object> v = new LinkedHashMap<>(); v.put("ok",true); v.put("bound",true); v.put("bedSn",sn);
        v.put("bindingId",number(binding,"id")); v.put("deviceId",binding.get("device_id")); v.put("deviceCode",binding.get("device_code"));
        long sensor = number(binding,"sensor_id");
        Map<String,Object> reading = one("select * from sensor_latest_readings where sensor_id=?",sensor);
        v.put("state","WAITING"); v.put("receivedAt",null);
        if (reading != null) {
            boolean online = instant(reading,"received_at").isAfter(Instant.now().minusSeconds(10));
            v.put("online",online); v.put("receivedAt",instant(reading,"received_at"));
            v.put("state",flag(reading,"standby") ? "STANDBY" : !online ? "OFFLINE" : !flag(reading,"sensor_ok") ? "ERROR" : flag(reading,"moving") ? "MOVING" : "STILL");
        }
        Map<String,Object> session = one("select * from sensor_workout_sessions where sensor_id=? and bed_id=? and user_id=? order by id desc limit 1",sensor,bed,user);
        v.put("session",session == null ? null : sessionView(session));
        return v;
    }
    public Map<String,Object> history(long user, String sn, Long before) {
        long bed = ownedBed(user,sn);
        List<Map<String,Object>> rows = db.queryForList("select * from sensor_workout_sessions where bed_id=? and user_id=? and id<? order by id desc limit 21",bed,user,before == null ? Long.MAX_VALUE : before);
        boolean more = rows.size()>20;
        List<Map<String,Object>> items = rows.stream().limit(20).map(this::sessionView).toList();
        Map<String,Object> result = new LinkedHashMap<>(); result.put("items",items);
        result.put("nextCursor",more ? items.getLast().get("id") : null); return result;
    }
    @Transactional
    public void unbind(long user, long bindingId) {
        one("select id from users where id=? for update",user);
        Map<String,Object> b = one("select * from sensor_device_bindings where id=? and unbound_at is null",bindingId);
        if (b == null) throw error(HttpStatus.NOT_FOUND,"BINDING_NOT_FOUND","绑定不存在");
        one("select id from devices where id=? for update",number(b,"bed_id"));
        one("select id from sensor_devices where id=? for update",number(b,"sensor_id"));
        if (one("select user_id from user_device_selections where user_id=? and device_id=?",user,number(b,"bed_id")) == null)
            throw error(HttpStatus.FORBIDDEN,"BED_NOT_OWNED","无权解绑这台核心床的传感器");
        db.update("update sensor_device_bindings set unbound_at=? where id=?",utc(Instant.now()),bindingId);
        db.update("update sensor_workout_sessions set status='COMPLETED',ended_at=last_motion_at,end_reason='UNBOUND' where sensor_id=? and status='ACTIVE'",number(b,"sensor_id"));
        db.update("delete from sensor_binding_challenges where sensor_id=?",number(b,"sensor_id"));
        committed("SENSOR_UNBOUND bindingId={} sensorId={} bedId={} userId={}",bindingId,b.get("sensor_id"),b.get("bed_id"),user);
    }
}
