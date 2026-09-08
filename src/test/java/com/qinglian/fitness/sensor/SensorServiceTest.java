package com.qinglian.fitness.sensor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import com.qinglian.fitness.common.ApiException;

@EnabledIfEnvironmentVariable(named="SENSOR_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/sensor_iot_test\\?.*")
class SensorServiceTest {
    static JdbcTemplate db;
    static TransactionTemplate tx;
    SensorService service;
    String deviceId, code;
    long sensorId, userId, bedId;
    String bedSn;
    @BeforeAll static void schema() throws Exception {
        var ds = new DriverManagerDataSource(System.getenv("SENSOR_TEST_DB_URL"),"root","");
        db = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("create table if not exists users(id bigint unsigned primary key)");
        db.execute("create table if not exists devices(id bigint unsigned primary key,serial_number varchar(64) unique)");
        db.execute("create table if not exists user_device_selections(user_id bigint unsigned,device_id bigint unsigned unique,primary key(user_id,device_id))");
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource("database/30-sensor-iot.sql"));
            ScriptUtils.executeSqlScript(connection,new FileSystemResource("database/31-sensor-keyless.sql"));
        }
    }
    void fixture() {
        service = new SensorService(db,new ObjectMapper());
        userId = Math.abs(new Random().nextLong(1,1000000000)); bedId = userId;
        bedSn = "BED-"+bedId;
        db.update("insert into users values(?)",userId);
        db.update("insert into devices values(?,?)",bedId,bedSn);
        db.update("insert into user_device_selections values(?,?)",userId,bedId);
        deviceId = "ARVELLO-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase();
        var registered = service.register(deviceId); code = registered.get("deviceCode").toString();
        sensorId = db.queryForObject("select id from sensor_devices where device_id=?",Long.class,deviceId);
    }
    void bound() {
        var challenge = service.challenge(userId,new SensorDtos.Claim(deviceId,code,bedSn));
        String token = challenge.get("challengeId").toString();
        service.confirm(deviceId,token); service.bind(userId,token);
    }
    SensorDtos.Reading reading(String boot,long seq,long count,boolean moving,boolean standby) {
        return new SensorDtos.Reading(3,deviceId,boot,seq,seq*1000,true,moving,standby,"continuous_cycle",count,"X",0.01,List.of(0.0,0.0,1.0),List.of(0.0,0.0,0.0),0.01);
    }
    void run(Runnable body) { tx.executeWithoutResult(status -> { try { fixture(); body.run(); } finally { status.setRollbackOnly(); } }); }
    @Test void bindingRequiresDeviceConfirmationAndOwnership() { run(() -> {
        String token = service.challenge(userId,new SensorDtos.Claim(deviceId,code,bedSn)).get("challengeId").toString();
        assertEquals("CLAIM_PENDING",assertThrows(ApiException.class,()->service.bind(userId,token)).code());
        assertThrows(ApiException.class,()->service.confirm(deviceId,"wrong"));
        service.confirm(deviceId,token); service.bind(userId,token);
        assertTrue((Boolean)service.latest(userId,bedSn).get("bound"));
        assertThrows(ApiException.class,()->service.latest(userId+1,bedSn));
        assertThrows(ApiException.class,()->service.bind(userId,token));
    }); }
    @Test void trailingThreeMinutesAreExcludedAndNextMotionStartsNewSession() { run(() -> {
        bound();
        service.receive(deviceId,reading("boot0001",1,10,false,false));
        service.receive(deviceId,reading("boot0001",2,10,true,false));
        Instant last = Instant.now().minusSeconds(181), start = last.minusSeconds(520);
        db.update("update sensor_workout_sessions set started_at=?,last_motion_at=? where sensor_id=?",SensorService.utc(start),SensorService.utc(last),sensorId);
        service.receive(deviceId,reading("boot0001",3,10,false,false));
        var session = (Map<?,?>)service.latest(userId,bedSn).get("session");
        assertEquals("COMPLETED",session.get("status")); assertEquals(520000L,session.get("durationMs"));
        assertEquals(session.get("lastMotionAt"),session.get("endedAt"));
        service.receive(deviceId,reading("boot0001",4,10,true,false));
        assertEquals(2,db.queryForObject("select count(*) from sensor_workout_sessions where sensor_id=?",Integer.class,sensorId));
    }); }
    @Test void briefPauseStaysInOneSessionAndDuplicatesDoNotDoubleCount() { run(() -> {
        bound(); service.receive(deviceId,reading("boot0001",1,10,false,false));
        service.receive(deviceId,reading("boot0001",2,10,true,false));
        service.receive(deviceId,reading("boot0001",3,11,false,false));
        service.receive(deviceId,reading("boot0001",4,12,true,false));
        var duplicate = service.receive(deviceId,reading("boot0001",4,12,true,false));
        assertTrue((Boolean)duplicate.get("duplicate"));
        var session = (Map<?,?>)service.latest(userId,bedSn).get("session");
        assertEquals(2L,session.get("repetitionCount"));
        assertEquals(1,db.queryForObject("select count(*) from sensor_workout_sessions where sensor_id=?",Integer.class,sensorId));
        service.receive(deviceId,reading("boot0001",5,12,false,true));
        assertEquals("STANDBY",service.latest(userId,bedSn).get("state"));
    }); }
    @Test void rebootOldUploadsAndCounterResetAreHandled() { run(() -> {
        bound(); service.receive(deviceId,reading("boot0001",1,5,true,false));
        assertEquals("COUNTER_RESET",assertThrows(ApiException.class,()->service.receive(deviceId,reading("boot0001",2,0,true,false))).code());
        service.receive(deviceId,reading("boot0002",1,5,true,false));
        assertEquals("OLD_BOOT",assertThrows(ApiException.class,()->service.receive(deviceId,reading("boot0001",3,7,true,false))).code());
        assertEquals(2,db.queryForObject("select count(*) from sensor_workout_sessions where sensor_id=?",Integer.class,sensorId));
    }); }
    @Test void unbindingPreservesHistoryAndStopsNewSessions() { run(() -> {
        bound(); service.receive(deviceId,reading("boot0001",1,0,true,false));
        long bindingId = ((Number)service.latest(userId,bedSn).get("bindingId")).longValue();
        service.unbind(userId,bindingId);
        service.receive(deviceId,reading("boot0001",2,1,true,false));
        assertFalse((Boolean)service.latest(userId,bedSn).get("bound"));
        assertEquals(1,((List<?>)service.history(userId,bedSn,null).get("items")).size());
    }); }

    @Test void firstUploadRegistersDeviceWithoutKeysAndRejectsInvalidOrDisabledIds() { run(() -> {
        db.update("delete from sensor_devices where id=?",sensorId);
        assertEquals("DEVICE_ID_MISMATCH",assertThrows(ApiException.class,
            ()->service.receive("ARVELLO-000000000000",reading("boot0001",1,0,false,false))).code());
        assertThrows(ApiException.class,()->service.receive(null,reading("boot0001",1,0,false,false)));
        service.receive(deviceId,reading("boot0001",1,0,false,false));
        service.receive(deviceId,reading("boot0001",2,0,false,false));
        assertEquals(1,db.queryForObject("select count(*) from sensor_devices where device_id=?",Integer.class,deviceId));
        var row = db.queryForMap("select key_hash,claim_hash from sensor_devices where device_id=?",deviceId);
        assertNull(row.get("key_hash")); assertNull(row.get("claim_hash"));
        assertFalse(service.register(deviceId).containsKey("deviceKey"));
        db.update("update sensor_devices set status='DISABLED' where device_id=?",deviceId);
        assertEquals("DEVICE_DISABLED",assertThrows(ApiException.class,
            ()->service.receive(deviceId,reading("boot0001",3,0,false,false))).code());
    }); }

    @Test void bindingCanRegisterDeviceBeforeItsFirstTelemetry() { run(() -> {
        db.update("delete from sensor_devices where id=?",sensorId);
        bound();
        assertTrue((Boolean)service.latest(userId,bedSn).get("bound"));
    }); }
}
