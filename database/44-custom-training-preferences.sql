USE hqc_plt;
SET NAMES utf8mb4;

SET @custom_goal_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'custom_courses' AND column_name = 'goal'),
    'SELECT 1',
    'ALTER TABLE custom_courses ADD COLUMN goal VARCHAR(20) NOT NULL DEFAULT ''核心强化'' AFTER summary'
);
PREPARE custom_goal_migration FROM @custom_goal_ddl;
EXECUTE custom_goal_migration;
DEALLOCATE PREPARE custom_goal_migration;

SET @custom_level_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'custom_courses' AND column_name = 'level'),
    'SELECT 1',
    'ALTER TABLE custom_courses ADD COLUMN level VARCHAR(10) NOT NULL DEFAULT ''初级'' AFTER goal'
);
PREPARE custom_level_migration FROM @custom_level_ddl;
EXECUTE custom_level_migration;
DEALLOCATE PREPARE custom_level_migration;

SET @custom_warmup_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'custom_courses' AND column_name = 'warmup_minutes'),
    'SELECT 1',
    'ALTER TABLE custom_courses ADD COLUMN warmup_minutes INT NOT NULL DEFAULT 5 AFTER level'
);
PREPARE custom_warmup_migration FROM @custom_warmup_ddl;
EXECUTE custom_warmup_migration;
DEALLOCATE PREPARE custom_warmup_migration;

SET @custom_rest_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'custom_courses' AND column_name = 'rest_seconds'),
    'SELECT 1',
    'ALTER TABLE custom_courses ADD COLUMN rest_seconds INT NOT NULL DEFAULT 30 AFTER warmup_minutes'
);
PREPARE custom_rest_migration FROM @custom_rest_ddl;
EXECUTE custom_rest_migration;
DEALLOCATE PREPARE custom_rest_migration;

SET @custom_set_count_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'custom_course_exercises' AND column_name = 'set_count'),
    'SELECT 1',
    'ALTER TABLE custom_course_exercises ADD COLUMN set_count INT NOT NULL DEFAULT 3 AFTER sort_order'
);
PREPARE custom_set_count_migration FROM @custom_set_count_ddl;
EXECUTE custom_set_count_migration;
DEALLOCATE PREPARE custom_set_count_migration;

SET @custom_repetitions_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'custom_course_exercises' AND column_name = 'repetitions'),
    'SELECT 1',
    'ALTER TABLE custom_course_exercises ADD COLUMN repetitions INT NOT NULL DEFAULT 10 AFTER set_count'
);
PREPARE custom_repetitions_migration FROM @custom_repetitions_ddl;
EXECUTE custom_repetitions_migration;
DEALLOCATE PREPARE custom_repetitions_migration;
