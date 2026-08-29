USE hqc_plt;

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

INSERT INTO training_plans(id, title, week_number, sessions_per_week, description, active, sort_order)
VALUES
    (2, '核心稳定计划', 1, 3, '用四周建立核心控制，每周三次，适合完成基础训练后继续进阶。', TRUE, 20),
    (3, '舒展恢复计划', 1, 2, '每周两次肩背和全身舒展，适合久坐、训练后恢复和轻量活动日。', TRUE, 30)
ON DUPLICATE KEY UPDATE
    title = VALUES(title), week_number = VALUES(week_number), sessions_per_week = VALUES(sessions_per_week),
    description = VALUES(description), active = VALUES(active), sort_order = VALUES(sort_order);

INSERT INTO training_plan_items(id, plan_id, course_id, day_offset, sort_order)
VALUES
    (4, 2, 2, 1, 10), (5, 2, 1, 3, 20), (6, 2, 2, 5, 30),
    (7, 3, 3, 2, 10), (8, 3, 3, 5, 20)
ON DUPLICATE KEY UPDATE
    plan_id = VALUES(plan_id), course_id = VALUES(course_id), day_offset = VALUES(day_offset), sort_order = VALUES(sort_order);
