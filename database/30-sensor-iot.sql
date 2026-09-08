-- Apply to the existing business database before deploying the sensor service.
CREATE TABLE IF NOT EXISTS sensor_devices (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 device_id VARCHAR(64) NOT NULL UNIQUE,
 device_code VARCHAR(64) NOT NULL UNIQUE,
 key_hash CHAR(64) NULL,
 claim_hash CHAR(64) NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
 created_at DATETIME(3) NOT NULL,
 last_seen_at DATETIME(3) NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sensor_device_bindings (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 sensor_id BIGINT UNSIGNED NOT NULL,
 bed_id BIGINT UNSIGNED NOT NULL,
 user_id BIGINT UNSIGNED NOT NULL,
 bound_at DATETIME(3) NOT NULL,
 unbound_at DATETIME(3) NULL,
 active_sensor BIGINT UNSIGNED GENERATED ALWAYS AS (IF(unbound_at IS NULL,sensor_id,NULL)) STORED,
 active_bed BIGINT UNSIGNED GENERATED ALWAYS AS (IF(unbound_at IS NULL,bed_id,NULL)) STORED,
 UNIQUE KEY uk_sensor_active (active_sensor),
 UNIQUE KEY uk_bed_active (active_bed),
 KEY idx_sensor_binding_history (bed_id,bound_at),
 FOREIGN KEY (sensor_id) REFERENCES sensor_devices(id),
 FOREIGN KEY (bed_id) REFERENCES devices(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sensor_binding_challenges (
 token_hash CHAR(64) PRIMARY KEY,
 sensor_id BIGINT UNSIGNED NOT NULL,
 bed_id BIGINT UNSIGNED NOT NULL,
 user_id BIGINT UNSIGNED NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 confirmed_at DATETIME(3) NULL,
 used_at DATETIME(3) NULL,
 KEY idx_challenge_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sensor_latest_readings (
 sensor_id BIGINT UNSIGNED PRIMARY KEY,
 boot_id VARCHAR(36) NOT NULL,
 sequence_number BIGINT NOT NULL,
 repetition_count BIGINT NOT NULL,
 moving BOOLEAN NULL,
 standby BOOLEAN NOT NULL,
 sensor_ok BOOLEAN NOT NULL,
 payload_json JSON NOT NULL,
 received_at DATETIME(3) NOT NULL,
 FOREIGN KEY (sensor_id) REFERENCES sensor_devices(id)
) ENGINE=InnoDB;

-- Keep boot identities to reject delayed uploads from a previous boot.
CREATE TABLE IF NOT EXISTS sensor_boots (
 sensor_id BIGINT UNSIGNED NOT NULL,
 boot_id VARCHAR(36) NOT NULL,
 first_seen_at DATETIME(3) NOT NULL,
 PRIMARY KEY (sensor_id,boot_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sensor_workout_sessions (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 sensor_id BIGINT UNSIGNED NOT NULL,
 bed_id BIGINT UNSIGNED NOT NULL,
 user_id BIGINT UNSIGNED NOT NULL,
 boot_id VARCHAR(36) NOT NULL,
 started_at DATETIME(3) NOT NULL,
 last_motion_at DATETIME(3) NOT NULL,
 ended_at DATETIME(3) NULL,
 start_count BIGINT NOT NULL,
 end_count BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
 end_reason VARCHAR(32) NULL,
 active_sensor BIGINT UNSIGNED GENERATED ALWAYS AS (IF(status='ACTIVE',sensor_id,NULL)) STORED,
 UNIQUE KEY uk_workout_active (active_sensor),
 KEY idx_workout_bed_user (bed_id,user_id,id),
 KEY idx_workout_expiry (status,last_motion_at)
) ENGINE=InnoDB;
