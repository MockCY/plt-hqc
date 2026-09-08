-- 使用服务器上已有的业务数据库，不创建新的数据库。
USE hqc_plt;

    focus_image_url VARCHAR(500) NULL,
    focus_parts VARCHAR(100) NULL,
    spring_sets JSON NULL,
    key_points TEXT NULL,
    common_mistakes TEXT NULL,
    instruction_audio_url VARCHAR(500) NULL,
CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    openid VARCHAR(64) NOT NULL,
    unionid VARCHAR(64) NULL,
    phone VARCHAR(32) NULL,
    country_code VARCHAR(8) NULL,
    nickname VARCHAR(40) NULL,
    avatar_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_openid (openid),
    UNIQUE KEY uk_users_phone (phone)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS auth_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    last_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_sessions_token_hash (token_hash),
    KEY idx_auth_sessions_user_id (user_id),
    KEY idx_auth_sessions_expires_at (expires_at),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS daily_online_users (
    user_id BIGINT UNSIGNED NOT NULL,
    online_date DATE NOT NULL,
    first_seen_at DATETIME(3) NOT NULL,
    PRIMARY KEY (online_date, user_id),
    KEY idx_daily_online_users_user (user_id, online_date),
    CONSTRAINT fk_daily_online_users_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_presence_visits (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    client_id VARCHAR(160) NOT NULL,
    online_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    offline_at DATETIME(3) NULL,
    end_reason VARCHAR(20) NULL,
    active_client VARCHAR(160) GENERATED ALWAYS AS (IF(offline_at IS NULL, client_id, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_presence_active_client (user_id, active_client),
    KEY idx_presence_user_history (user_id, online_at, id),
    KEY idx_presence_expiry (offline_at, last_seen_at),
    CONSTRAINT fk_presence_visits_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS courses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(80) NOT NULL,
    type VARCHAR(30) NOT NULL,
    duration_minutes INT NOT NULL,
    level VARCHAR(30) NOT NULL,
    equipment VARCHAR(80) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    introduction TEXT NULL,
    audience TEXT NULL,
    training_tags VARCHAR(200) NULL,
    cover_image VARCHAR(500) NULL,
    video_url VARCHAR(500) NULL,
    video_cover_image VARCHAR(500) NULL,
    video_duration_seconds INT NULL,
    view_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_courses_status_sort (status, sort_order)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exercises (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    body_part VARCHAR(30) NOT NULL,
    level VARCHAR(30) NOT NULL,
    equipment VARCHAR(80) NOT NULL,
    suggested_sets INT NOT NULL DEFAULT 2,
    target VARCHAR(40) NOT NULL,
    cue VARCHAR(500) NOT NULL,
    safety_tip VARCHAR(500) NOT NULL,
    cover_image VARCHAR(500) NULL,
    video_url VARCHAR(500) NULL,
    video_cover_image VARCHAR(500) NULL,
    video_duration_seconds INT NULL,
    background_music_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_exercises_status_sort (status, sort_order),
    KEY idx_exercises_body_part (body_part)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS course_exercises (
    course_id BIGINT UNSIGNED NOT NULL,
    exercise_id BIGINT UNSIGNED NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    duration_seconds INT NULL,
    target VARCHAR(40) NULL,
    training_sets JSON NULL,
    recommended_plays INT NULL,
    PRIMARY KEY (course_id, exercise_id),
    CONSTRAINT fk_course_exercises_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_exercises_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS course_views (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id BIGINT UNSIGNED NOT NULL,
    visitor_key VARCHAR(80) NOT NULL,
    viewed_on DATE NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_views_daily (course_id, visitor_key, viewed_on),
    KEY idx_course_views_date (viewed_on),
    CONSTRAINT fk_course_views_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS training_plans (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(80) NOT NULL,
    week_number INT NOT NULL DEFAULT 1,
    sessions_per_week INT NOT NULL DEFAULT 3,
    description VARCHAR(300) NULL,
    subtitle VARCHAR(160) NULL,
    cover_image VARCHAR(500) NULL,
    level VARCHAR(30) NULL,
    training_scene VARCHAR(30) NULL,
    session_minutes INT NULL,
    benefit_one VARCHAR(80) NULL,
    benefit_two VARCHAR(80) NULL,
    benefit_three VARCHAR(80) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_training_plans_active_sort (active, sort_order)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS training_plan_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id BIGINT UNSIGNED NOT NULL,
    course_id BIGINT UNSIGNED NOT NULL,
    day_offset INT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_plan_items_plan (plan_id, day_offset, sort_order),
    CONSTRAINT fk_plan_items_plan FOREIGN KEY (plan_id) REFERENCES training_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_items_course FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS workout_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    course_id BIGINT UNSIGNED NOT NULL,
    duration_minutes INT NOT NULL,
    completion_percent INT NOT NULL DEFAULT 100,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_workout_records_user_completed (user_id, completed_at),
    KEY idx_workout_records_course (course_id),
    CONSTRAINT fk_workout_records_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_workout_records_course FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_settings (
    user_id BIGINT UNSIGNED NOT NULL,
    reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sound_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_favorites (
    user_id BIGINT UNSIGNED NOT NULL,
    item_type VARCHAR(20) NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, item_type, item_id),
    KEY idx_user_favorites_type_item (item_type, item_id),
    CONSTRAINT fk_user_favorites_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS custom_courses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(80) NOT NULL,
    duration_minutes INT NOT NULL,
    summary VARCHAR(300) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_custom_courses_user (user_id, created_at),
    CONSTRAINT fk_custom_courses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS custom_course_exercises (
    custom_course_id BIGINT UNSIGNED NOT NULL,
    exercise_id BIGINT UNSIGNED NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (custom_course_id, exercise_id),
    CONSTRAINT fk_custom_course_items_course FOREIGN KEY (custom_course_id) REFERENCES custom_courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_custom_course_items_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS custom_workout_records (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    custom_course_id BIGINT UNSIGNED NOT NULL,
    duration_minutes INT NOT NULL,
    completion_percent INT NOT NULL DEFAULT 100,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_custom_workouts_user_completed (user_id, completed_at),
    CONSTRAINT fk_custom_workouts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_custom_workouts_course FOREIGN KEY (custom_course_id) REFERENCES custom_courses(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_plan_selections (
    user_id BIGINT UNSIGNED NOT NULL,
    plan_id BIGINT UNSIGNED NOT NULL,
    selected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    KEY idx_user_plan_selections_plan (plan_id),
    CONSTRAINT fk_user_plan_selections_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_plan_selections_plan FOREIGN KEY (plan_id) REFERENCES training_plans(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS device_models (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    brand VARCHAR(30) NOT NULL DEFAULT 'ARVELLO',
    sn_prefix VARCHAR(12) NOT NULL,
    next_serial_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_models_name (name),
    UNIQUE KEY uk_device_models_sn_prefix (sn_prefix)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    serial_number VARCHAR(64) NOT NULL,
    qr_token VARCHAR(64) NOT NULL,
    device_model VARCHAR(100) NOT NULL,
    brand VARCHAR(32) NULL,
    device_name VARCHAR(100) NULL,
    device_source VARCHAR(20) NOT NULL DEFAULT 'OWN',
    last_bound_at DATETIME(3) NULL,
    last_unbound_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_devices_serial_number (serial_number),
    UNIQUE KEY uk_devices_qr_token (qr_token),
    KEY idx_devices_device_model (device_model),
    KEY idx_devices_source (device_source),
    KEY idx_devices_brand (brand),
    CONSTRAINT chk_devices_brand_source CHECK (
        (device_source = 'OWN' AND brand IN ('Manhart', 'ARVELLO'))
        OR (device_source = 'THIRD_PARTY' AND brand IS NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_device_selections (
    user_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    selected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, device_id),
    UNIQUE KEY uk_user_device_device (device_id),
    CONSTRAINT fk_user_device_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_device_device FOREIGN KEY (device_id) REFERENCES devices(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_feedback (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    category VARCHAR(30) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    contact VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_feedback_user_created (user_id, created_at),
    CONSTRAINT fk_user_feedback_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS training_detail_visits (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    detail_type VARCHAR(30) NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    legacy_record_id BIGINT UNSIGNED NULL,
    visited_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_training_visit_legacy (detail_type, legacy_record_id),
    KEY idx_training_visit_user_date (user_id, visited_at),
    CONSTRAINT fk_training_visit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS training_activity (
    user_id BIGINT UNSIGNED NOT NULL,
    activity_type VARCHAR(20) NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    started_at DATETIME(3) NOT NULL,
    training_date DATE NOT NULL,
    active_seconds INT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, activity_type, item_id, started_at, training_date),
    CONSTRAINT fk_training_activity_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS campaign_checkins (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    campaign_code VARCHAR(40) NOT NULL,
    checkin_date DATE NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_campaign_checkins_day (user_id, campaign_code, checkin_date),
    KEY idx_campaign_checkins_campaign_date (campaign_code, checkin_date),
    CONSTRAINT fk_campaign_checkins_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

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
