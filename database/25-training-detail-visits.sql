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

INSERT INTO training_detail_visits(user_id, detail_type, item_id, legacy_record_id, visited_at)
SELECT user_id, 'LEGACY_COURSE', course_id, id, completed_at
FROM workout_records WHERE completion_percent >= 80
ON DUPLICATE KEY UPDATE legacy_record_id = VALUES(legacy_record_id);

INSERT INTO training_detail_visits(user_id, detail_type, item_id, legacy_record_id, visited_at)
SELECT user_id, 'LEGACY_CUSTOM', custom_course_id, id, completed_at
FROM custom_workout_records WHERE completion_percent >= 80
ON DUPLICATE KEY UPDATE legacy_record_id = VALUES(legacy_record_id);
