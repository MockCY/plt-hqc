USE hqc_plt;
SET NAMES utf8mb4;

-- Run 38-campaign-presentation.sql first on existing databases.
-- Only the first addition copies legacy artwork; re-runs preserve independently edited/cleared banners.
SET @campaign_banner_is_new = NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'campaigns' AND column_name = 'banner_image'
);
SET @campaign_banner_ddl = IF(
    @campaign_banner_is_new,
    'ALTER TABLE campaigns ADD COLUMN banner_image VARCHAR(1024) NULL AFTER title',
    'SELECT 1'
);
PREPARE campaign_banner_migration FROM @campaign_banner_ddl;
EXECUTE campaign_banner_migration;
DEALLOCATE PREPARE campaign_banner_migration;

-- Keep all original content and timestamps unchanged, including existing detail posters.
SET @campaign_banner_copy = IF(
    @campaign_banner_is_new,
    'UPDATE campaigns SET banner_image = poster_image, updated_at = updated_at WHERE poster_image IS NOT NULL',
    'SELECT 1'
);
PREPARE campaign_banner_migration FROM @campaign_banner_copy;
EXECUTE campaign_banner_migration;
DEALLOCATE PREPARE campaign_banner_migration;
