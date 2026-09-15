USE hqc_plt;
SET NAMES utf8mb4;

-- Add configurable activity artwork and button text without changing existing content or check-ins.
-- Safe to re-run, including on databases initialized from the current 08-admin-console.sql.
SET @campaign_poster_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'campaigns' AND column_name = 'poster_image'
    ),
    'SELECT 1',
    'ALTER TABLE campaigns ADD COLUMN poster_image VARCHAR(1024) NULL AFTER title'
);
PREPARE campaign_presentation_migration FROM @campaign_poster_ddl;
EXECUTE campaign_presentation_migration;
DEALLOCATE PREPARE campaign_presentation_migration;

SET @campaign_button_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'campaigns' AND column_name = 'button_text'
    ),
    'SELECT 1',
    'ALTER TABLE campaigns ADD COLUMN button_text VARCHAR(20) NOT NULL DEFAULT ''查看活动'' AFTER poster_image'
);
PREPARE campaign_presentation_migration FROM @campaign_button_ddl;
EXECUTE campaign_presentation_migration;
DEALLOCATE PREPARE campaign_presentation_migration;
