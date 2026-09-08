package com.qinglian.fitness.sensor;

import com.qinglian.fitness.admin.AdminDtos.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.qinglian.fitness.sensor.SensorService.*;

@Service
@Transactional(readOnly=true)
public class AdminSensorService {
    private final JdbcTemplate db;
    private final tools.jackson.databind.ObjectMapper json;
    public AdminSensorService(JdbcTemplate db,tools.jackson.databind.ObjectMapper json) { this.db=db; this.json=json; }
    private static final String DEVICES = """
        select s.id,s.device_id,s.device_code,s.status,s.created_at,s.last_seen_at,
         b.id binding_id,b.bed_id,b.bound_at,d.serial_number bed_sn,u.id user_id,u.nickname user_name,u.phone user_phone,
         l.boot_id,l.sequence_number,l.repetition_count,l.moving,l.standby,l.sensor_ok,l.received_at,
         case when s.status<>'ACTIVE' then 'DISABLED' when l.sensor_id is null then 'WAITING'
          when l.standby=1 then 'STANDBY' when l.received_at<=utc_timestamp(3)-interval 10 second then 'OFFLINE'
          when l.sensor_ok=0 then 'ERROR' when l.moving=1 then 'MOVING' else 'STILL' end state
        from sensor_devices s left join sensor_device_bindings b on b.sensor_id=s.id and b.unbound_at is null
        left join devices d on d.id=b.bed_id left join user_device_selections own on own.device_id=b.bed_id
        left join users u on u.id=own.user_id left join sensor_latest_readings l on l.sensor_id=s.id
        """;
    private static final String WORKOUTS = """
        from sensor_workout_sessions w join sensor_devices s on s.id=w.sensor_id
        left join devices d on d.id=w.bed_id left join users u on u.id=w.user_id
        """;
    private static final Map<String,String> FIELDS = Map.ofEntries(
        Map.entry("device_id","deviceId"),Map.entry("device_code","deviceCode"),Map.entry("created_at","createdAt"),
        Map.entry("last_seen_at","lastSeenAt"),Map.entry("binding_id","bindingId"),Map.entry("bed_id","bedId"),
        Map.entry("bed_sn","bedSn"),Map.entry("bound_at","boundAt"),Map.entry("unbound_at","unboundAt"),
        Map.entry("user_id","userId"),Map.entry("user_name","userName"),Map.entry("user_phone","userPhone"),
        Map.entry("boot_id","bootId"),Map.entry("sequence_number","sequence"),Map.entry("repetition_count","repetitionCount"),
        Map.entry("sensor_ok","sensorOk"),Map.entry("received_at","receivedAt"),Map.entry("sensor_id","sensorId"),
        Map.entry("started_at","startedAt"),Map.entry("last_motion_at","lastMotionAt"),Map.entry("ended_at","endedAt"),
        Map.entry("start_count","startCount"),Map.entry("end_count","endCount"),Map.entry("end_reason","endReason"));
    private Map<String,Object> view(Map<String,Object> row) {
        Map<String,Object> result=new LinkedHashMap<>();
        row.forEach((key,value)->result.put(FIELDS.getOrDefault(key,key),value instanceof LocalDateTime t ? t.toInstant(ZoneOffset.UTC) : value));
        if (row.containsKey("started_at")) {
            result.put("durationMs",TrainingWindow.elapsedMs(instant(row,"started_at"),instant(row,"last_motion_at")));
            result.put("repetitionCount",Math.max(0,number(row,"end_count")-number(row,"start_count")));
        }
        return result;
    }
    private void paging(int page,int size) {
        if (page<1 || page>1000000 || size<1 || size>100) throw error(HttpStatus.BAD_REQUEST,"INVALID_PAGE","分页参数不正确");
    }
    private String option(String value,Set<String> options) {
        if (value==null || value.isBlank()) return "ALL";
        if (!options.contains(value)) throw error(HttpStatus.BAD_REQUEST,"INVALID_FILTER","筛选条件不正确");
        return value;
    }
    private void search(String query,String columns,StringBuilder where,List<Object> args) {
        if (query==null || query.isBlank()) return;
        if (query.length()>100) throw error(HttpStatus.BAD_REQUEST,"INVALID_FILTER","搜索内容过长");
        String pattern="%"+query.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        where.append(" and concat_ws(' ',").append(columns).append(") like ? escape '!'"); args.add(pattern);
    }
    private void idFilter(String column,Long value,StringBuilder where,List<Object> args) {
        if (value==null) return;
        if (value<1) throw error(HttpStatus.BAD_REQUEST,"INVALID_FILTER","编号必须为正整数");
        where.append(" and ").append(column).append("=?"); args.add(value);
    }
    private PageResult<Map<String,Object>> page(String select,String from,String where,List<Object> args,String order,int page,int size) {
        paging(page,size);
        long count=db.queryForObject("select count(*) "+from+where,Long.class,args.toArray());
        List<Object> paged=new ArrayList<>(args); paged.add(size); paged.add((page-1)*size);
        return new PageResult<>(db.queryForList(select+from+where+order+" limit ? offset ?",paged.toArray()).stream().map(this::view).toList(),count,page,size);
    }
    public PageResult<Map<String,Object>> sensors(String query,String state,String binding,Long bedId,Long userId,int page,int size) {
        state=option(state,Set.of("ALL","WAITING","MOVING","STILL","ERROR","OFFLINE","STANDBY","DISABLED"));
        binding=option(binding,Set.of("ALL","BOUND","UNBOUND"));
        StringBuilder where=new StringBuilder(" where 1=1"); List<Object> args=new ArrayList<>();
        search(query,"device_id,device_code,bed_sn,user_name,user_phone,user_id",where,args);
        if (!state.equals("ALL")) { where.append(" and state=?"); args.add(state); }
        if (!binding.equals("ALL")) where.append(binding.equals("BOUND") ? " and binding_id is not null" : " and binding_id is null");
        idFilter("bed_id",bedId,where,args); idFilter("user_id",userId,where,args);
        return page("select * ","from ("+DEVICES+") sensors",where.toString(),args," order by id desc",page,size);
    }
    public Map<String,Object> detail(long id) {
        var rows=db.queryForList(DEVICES+" where s.id=?",id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"SENSOR_NOT_FOUND","传感器不存在");
        Map<String,Object> result=view(rows.getFirst());
        // Select only sensor telemetry fields; credentials are never exposed to the administrator UI.
        var latest=db.queryForList("select payload_json from sensor_latest_readings where sensor_id=?",id);
        if (!latest.isEmpty()) {
            var payload=json.readTree(latest.getFirst().get("payload_json").toString());
            Map<String,Object> telemetry=new LinkedHashMap<>();
            for (String key:List.of("uptimeMs","countType","motionAxis","motionAxisG","accelG","gyroDps","activityG"))
                if (payload.has(key)) telemetry.put(key,payload.get(key));
            result.put("telemetry",telemetry);
        }
        return result;
    }
    public PageResult<Map<String,Object>> bindings(long sensorId,int page,int size) {
        detail(sensorId);
        return page("select b.id,b.sensor_id,b.bed_id,b.user_id,b.bound_at,b.unbound_at,d.serial_number bed_sn,u.nickname user_name,u.phone user_phone ",
            "from sensor_device_bindings b left join devices d on d.id=b.bed_id left join users u on u.id=b.user_id",
            " where b.sensor_id=?",List.of(sensorId)," order by b.id desc",page,size);
    }
    public Map<String,Object> workouts(String query,String status,Long sensorId,Long bedId,Long userId,LocalDate from,LocalDate to,int page,int size) {
        status=option(status,Set.of("ALL","ACTIVE","COMPLETED"));
        StringBuilder where=new StringBuilder(" where 1=1"); List<Object> args=new ArrayList<>();
        search(query,"s.device_id,s.device_code,d.serial_number,u.nickname,u.phone,w.user_id",where,args);
        idFilter("w.sensor_id",sensorId,where,args); idFilter("w.bed_id",bedId,where,args); idFilter("w.user_id",userId,where,args);
        if (!status.equals("ALL")) { where.append(" and w.status=?"); args.add(status); }
        if (from!=null && to!=null && from.isAfter(to)) throw error(HttpStatus.BAD_REQUEST,"INVALID_DATE_RANGE","开始日期不能晚于结束日期");
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        if (from!=null) { where.append(" and w.started_at>=?"); args.add(utc(from.atStartOfDay(zone).toInstant())); }
        if (to!=null) { where.append(" and w.started_at<?"); args.add(utc(to.plusDays(1).atStartOfDay(zone).toInstant())); }
        var records=page("select w.id,w.sensor_id,w.bed_id,w.user_id,w.started_at,w.last_motion_at,w.ended_at,w.start_count,w.end_count,w.status,w.end_reason,s.device_id,s.device_code,d.serial_number bed_sn,u.nickname user_name,u.phone user_phone ",
            WORKOUTS,where.toString(),args," order by w.id desc",page,size);
        var sum=db.queryForMap("select coalesce(sum(greatest(0,w.end_count-w.start_count)),0) repetitions,coalesce(sum(greatest(0,timestampdiff(microsecond,w.started_at,w.last_motion_at)/1000)),0) durationMs,coalesce(sum(w.status='ACTIVE'),0) activeSessions "+WORKOUTS+where,args.toArray());
        return Map.of("items",records.items(),"total",records.total(),"page",page,"pageSize",size,"summary",sum);
    }
    private void lock(long id) {
        if (db.queryForList("select id from sensor_devices where id=? for update",id).isEmpty())
            throw error(HttpStatus.NOT_FOUND,"SENSOR_NOT_FOUND","传感器不存在");
    }
    public Map<String,Object> workout(long id) {
        var rows=db.queryForList("select w.*,s.device_id,s.device_code,d.serial_number bed_sn,u.nickname user_name,u.phone user_phone "+WORKOUTS+" where w.id=?",id);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"WORKOUT_NOT_FOUND","训练记录不存在");
        return view(rows.getFirst());
    }
    @Transactional
    public void unbind(long id,long bindingId) {
        lock(id);
        int changed=db.update("update sensor_device_bindings set unbound_at=? where id=? and sensor_id=? and unbound_at is null",utc(Instant.now()),bindingId,id);
        if (changed==0) throw error(HttpStatus.CONFLICT,"BINDING_CHANGED","绑定已发生变化，请刷新后重试");
        end(id,"ADMIN_UNBOUND");
    }
    @Transactional
    public void status(long id,String status) {
        if (!Set.of("ACTIVE","DISABLED").contains(status)) throw error(HttpStatus.BAD_REQUEST,"INVALID_STATUS","传感器状态不正确");
        lock(id); db.update("update sensor_devices set status=? where id=?",status,id);
        if (status.equals("DISABLED")) end(id,"DISABLED");
    }
    private void end(long id,String reason) {
        db.update("update sensor_workout_sessions set status='COMPLETED',ended_at=last_motion_at,end_reason=? where sensor_id=? and status='ACTIVE'",reason,id);
        db.update("delete from sensor_binding_challenges where sensor_id=?",id);
    }
}
