DROP TABLE IF EXISTS user_settings;
DROP TABLE IF EXISTS campaign_checkins;
DROP TABLE IF EXISTS user_feedback;
DROP TABLE IF EXISTS user_plan_selections;
DROP TABLE IF EXISTS custom_course_exercises;
DROP TABLE IF EXISTS custom_workout_records;
DROP TABLE IF EXISTS custom_courses;
DROP TABLE IF EXISTS user_favorites;
DROP TABLE IF EXISTS workout_records;
DROP TABLE IF EXISTS training_plan_items;
DROP TABLE IF EXISTS training_plans;
DROP TABLE IF EXISTS course_exercises;
DROP TABLE IF EXISTS exercises;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS auth_sessions;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid VARCHAR(64) NOT NULL UNIQUE,
    unionid VARCHAR(64),
    phone VARCHAR(32),
    country_code VARCHAR(8),
    nickname VARCHAR(40),
    avatar_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE auth_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(80) NOT NULL,
    type VARCHAR(30) NOT NULL,
    duration_minutes INT NOT NULL,
    level VARCHAR(30) NOT NULL,
    equipment VARCHAR(80) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    cover_image VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE exercises (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    body_part VARCHAR(30) NOT NULL,
    level VARCHAR(30) NOT NULL,
    equipment VARCHAR(80) NOT NULL,
    suggested_sets INT NOT NULL,
    target VARCHAR(40) NOT NULL,
    cue VARCHAR(500) NOT NULL,
    safety_tip VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE course_exercises (
    course_id BIGINT NOT NULL,
    exercise_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    duration_seconds INT,
    target VARCHAR(40),
    PRIMARY KEY (course_id, exercise_id),
    FOREIGN KEY (course_id) REFERENCES courses(id),
    FOREIGN KEY (exercise_id) REFERENCES exercises(id)
);

CREATE TABLE training_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(80) NOT NULL,
    week_number INT NOT NULL,
    sessions_per_week INT NOT NULL,
    description VARCHAR(300),
    active BOOLEAN NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE training_plan_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    day_offset INT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    FOREIGN KEY (plan_id) REFERENCES training_plans(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
);

CREATE TABLE workout_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    duration_minutes INT NOT NULL,
    completion_percent INT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id)
);

CREATE TABLE user_settings (
    user_id BIGINT PRIMARY KEY,
    reminder_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sound_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE user_favorites (
    user_id BIGINT NOT NULL,
    item_type VARCHAR(20) NOT NULL,
    item_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, item_type, item_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE custom_courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(80) NOT NULL,
    duration_minutes INT NOT NULL,
    summary VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE custom_course_exercises (
    custom_course_id BIGINT NOT NULL,
    exercise_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (custom_course_id, exercise_id),
    FOREIGN KEY (custom_course_id) REFERENCES custom_courses(id) ON DELETE CASCADE,
    FOREIGN KEY (exercise_id) REFERENCES exercises(id)
);

CREATE TABLE custom_workout_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    custom_course_id BIGINT NOT NULL,
    duration_minutes INT NOT NULL,
    completion_percent INT NOT NULL DEFAULT 100,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (custom_course_id) REFERENCES custom_courses(id) ON DELETE CASCADE
);

CREATE TABLE user_plan_selections (
    user_id BIGINT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    selected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (plan_id) REFERENCES training_plans(id)
);

CREATE TABLE user_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    contact VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE campaign_checkins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    campaign_code VARCHAR(40) NOT NULL,
    checkin_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, campaign_code, checkin_date),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
