-- Apply once before deploying V4. V3 data and its legacy count/time semantics are retained.
ALTER TABLE sensor_devices
 ADD COLUMN serial_number VARCHAR(64) NULL,
 ADD COLUMN model VARCHAR(64) NULL,
 ADD COLUMN firmware_version VARCHAR(32) NULL;

ALTER TABLE sensor_latest_readings
 ADD COLUMN schema_version INT NOT NULL DEFAULT 3,
 ADD COLUMN training_state VARCHAR(16) NULL;

-- Unknown-time/offline summaries are durably retained without inventing a current owner or date.
ALTER TABLE sensor_workout_sessions
 MODIFY COLUMN bed_id BIGINT UNSIGNED NULL,
 MODIFY COLUMN user_id BIGINT UNSIGNED NULL,
 MODIFY COLUMN started_at DATETIME(3) NULL,
 MODIFY COLUMN last_motion_at DATETIME(3) NULL,
 ADD COLUMN schema_version INT NOT NULL DEFAULT 3,
 ADD COLUMN device_session_id VARCHAR(96) NULL,
 ADD COLUMN binding_id BIGINT UNSIGNED NULL,
 ADD COLUMN active_duration_ms BIGINT NULL,
 ADD COLUMN training_state VARCHAR(16) NULL,
 ADD COLUMN time_valid BOOLEAN NOT NULL DEFAULT TRUE,
 ADD COLUMN time_quality VARCHAR(16) NOT NULL DEFAULT 'SERVER',
 ADD COLUMN ownership_status VARCHAR(16) NOT NULL DEFAULT 'ASSIGNED',
 ADD COLUMN start_uptime_ms BIGINT NULL,
 ADD COLUMN end_uptime_ms BIGINT NULL,
 ADD COLUMN average_period_ms BIGINT NULL,
 ADD COLUMN min_period_ms BIGINT NULL,
 ADD COLUMN max_period_ms BIGINT NULL,
 ADD COLUMN summary_received BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN summary_payload_json JSON NULL,
 ADD COLUMN last_received_at DATETIME(3) NULL,
 ADD UNIQUE KEY uk_workout_device_session (sensor_id,device_session_id),
 ADD KEY idx_workout_pending (ownership_status,id);

-- Old installations left sensor bindings open after the bed changed owner. Seal those boundaries
-- before processing historical summaries so a current account never inherits an old workout.
UPDATE sensor_device_bindings b
 JOIN devices d ON d.id=b.bed_id
 LEFT JOIN user_device_selections own ON own.device_id=b.bed_id
 SET b.unbound_at=GREATEST(b.bound_at,COALESCE(d.last_unbound_at,own.selected_at,UTC_TIMESTAMP(3)))
 WHERE b.unbound_at IS NULL AND (own.user_id IS NULL OR own.user_id<>b.user_id);
