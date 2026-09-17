USE hqc_plt;
SET NAMES utf8mb4;

SET @plan_cycle_days_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'cycle_days'
    ),
    'SELECT 1',
    'ALTER TABLE training_plans ADD COLUMN cycle_days INT NULL AFTER sessions_per_week'
);
PREPARE plan_cycle_days_migration FROM @plan_cycle_days_ddl;
EXECUTE plan_cycle_days_migration;
DEALLOCATE PREPARE plan_cycle_days_migration;

UPDATE training_plans p
SET p.cycle_days = GREATEST(
    COALESCE(p.cycle_days, 0),
    COALESCE((SELECT COUNT(*) FROM training_plan_days d WHERE d.plan_id = p.id), 0),
    1
);

ALTER TABLE training_plans MODIFY COLUMN cycle_days INT NOT NULL DEFAULT 1;
