USE hqc_plt;
SET NAMES utf8mb4;

SET @plan_detail_image_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'detail_image'
    ),
    'SELECT 1',
    'ALTER TABLE training_plans ADD COLUMN detail_image VARCHAR(500) NULL AFTER cover_image'
);
PREPARE plan_day_migration FROM @plan_detail_image_ddl;
EXECUTE plan_day_migration;
DEALLOCATE PREPARE plan_day_migration;

CREATE TABLE IF NOT EXISTS training_plan_days (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id BIGINT UNSIGNED NOT NULL,
    day_number INT NOT NULL,
    title VARCHAR(80) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_day_number (plan_id, day_number),
    KEY idx_plan_days_plan (plan_id, sort_order),
    CONSTRAINT fk_plan_days_plan FOREIGN KEY (plan_id) REFERENCES training_plans(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS training_plan_day_exercises (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_day_id BIGINT UNSIGNED NOT NULL,
    exercise_id BIGINT UNSIGNED NOT NULL,
    repetitions INT NOT NULL DEFAULT 10,
    set_count INT NOT NULL DEFAULT 2,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_day_exercise (plan_day_id, exercise_id),
    KEY idx_plan_day_exercises_day (plan_day_id, sort_order),
    CONSTRAINT fk_plan_day_exercises_day FOREIGN KEY (plan_day_id) REFERENCES training_plan_days(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_day_exercises_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(id)
) ENGINE=InnoDB;

SET @plan_repetitions_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'training_plan_day_exercises' AND column_name = 'repetitions'
    ),
    'SELECT 1',
    'ALTER TABLE training_plan_day_exercises ADD COLUMN repetitions INT NOT NULL DEFAULT 10 AFTER exercise_id'
);
PREPARE plan_day_migration FROM @plan_repetitions_ddl;
EXECUTE plan_day_migration;
DEALLOCATE PREPARE plan_day_migration;

SET @plan_set_count_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'training_plan_day_exercises' AND column_name = 'set_count'
    ),
    'SELECT 1',
    'ALTER TABLE training_plan_day_exercises ADD COLUMN set_count INT NOT NULL DEFAULT 2 AFTER repetitions'
);
PREPARE plan_day_migration FROM @plan_set_count_ddl;
EXECUTE plan_day_migration;
DEALLOCATE PREPARE plan_day_migration;

CREATE TABLE IF NOT EXISTS user_plan_day_completions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    plan_id BIGINT UNSIGNED NOT NULL,
    day_number INT NOT NULL,
    completed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_plan_day_completion (user_id, plan_id, day_number),
    KEY idx_plan_day_completions_user (user_id, plan_id),
    CONSTRAINT fk_plan_day_completions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_day_completions_plan FOREIGN KEY (plan_id) REFERENCES training_plans(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TEMPORARY TABLE plan_day_migration_targets (plan_id BIGINT UNSIGNED PRIMARY KEY)
SELECT DISTINCT i.plan_id
FROM training_plan_items i
WHERE NOT EXISTS (
    SELECT 1 FROM training_plan_days d WHERE d.plan_id = i.plan_id
);

INSERT IGNORE INTO training_plan_days(plan_id, day_number, title, sort_order)
SELECT i.plan_id, i.day_offset + 1, COALESCE(MIN(c.title), CONCAT('第 ', i.day_offset + 1, ' 天训练')), (i.day_offset + 1) * 10
FROM training_plan_items i
JOIN plan_day_migration_targets target ON target.plan_id = i.plan_id
JOIN courses c ON c.id = i.course_id
GROUP BY i.plan_id, i.day_offset;

INSERT IGNORE INTO training_plan_day_exercises(plan_day_id, exercise_id, sort_order)
SELECT d.id, ce.exercise_id, i.sort_order * 100 + ce.sort_order
FROM training_plan_items i
JOIN plan_day_migration_targets target ON target.plan_id = i.plan_id
JOIN training_plan_days d ON d.plan_id = i.plan_id AND d.day_number = i.day_offset + 1
JOIN course_exercises ce ON ce.course_id = i.course_id;

DROP TEMPORARY TABLE plan_day_migration_targets;

UPDATE training_plans SET detail_image = cover_image WHERE detail_image IS NULL;
