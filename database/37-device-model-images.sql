USE hqc_plt;

-- Each device model may have its own image. Leave existing models unconfigured.
-- Safe to re-run, including on databases initialized from the current 01-schema.sql.
SET @device_model_image_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'device_models' AND column_name = 'image_url'
    ),
    'SELECT 1',
    'ALTER TABLE device_models ADD COLUMN image_url VARCHAR(500) NULL AFTER sn_prefix'
);
PREPARE device_model_image_migration FROM @device_model_image_ddl;
EXECUTE device_model_image_migration;
DEALLOCATE PREPARE device_model_image_migration;
