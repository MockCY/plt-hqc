package com.qinglian.fitness.sensor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import static com.qinglian.fitness.sensor.SensorService.*;

/** Called inside the account/bed transaction, before its ownership row disappears. */
@Service
public class SensorOwnershipService {
    private final JdbcTemplate db;
    public SensorOwnershipService(JdbcTemplate db) { this.db=db; }
    public void releaseBed(long user,long bed) {
        db.queryForList("select id from devices where id=? for update",bed);
        var bindings=db.queryForList("select b.id,b.sensor_id from sensor_device_bindings b where b.bed_id=? and b.user_id=? and b.unbound_at is null",bed,user);
        for (var b:bindings) {
            long sensor=number(b,"sensor_id");
            db.queryForList("select id from sensor_devices where id=? for update",sensor);
            db.update("update sensor_device_bindings set unbound_at=? where id=? and unbound_at is null",utc(Instant.now()),b.get("id"));
            db.update("update sensor_workout_sessions set status='COMPLETED',training_state='idle',ended_at=last_motion_at,end_reason='BED_UNBOUND' where sensor_id=? and status='ACTIVE'",sensor);
            db.update("delete from sensor_binding_challenges where sensor_id=?",sensor);
        }
    }
    public void releaseUser(long user) {
        db.queryForList("select id from users where id=? for update",user);
        for (var row:db.queryForList("select device_id from user_device_selections where user_id=? order by device_id",user))
            releaseBed(user,number(row,"device_id"));
    }
}
