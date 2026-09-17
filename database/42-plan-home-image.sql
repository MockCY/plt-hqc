USE hqc_plt;
SET NAMES utf8mb4;

SET @plan_home_image_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'home_image'
    ),
    'SELECT 1',
    'ALTER TABLE training_plans ADD COLUMN home_image VARCHAR(500) NULL AFTER detail_image'
);
PREPARE plan_home_image_migration FROM @plan_home_image_ddl;
EXECUTE plan_home_image_migration;
DEALLOCATE PREPARE plan_home_image_migration;
