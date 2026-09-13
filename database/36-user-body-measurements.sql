USE hqc_plt;

-- Optional body measurements; leave existing users unset instead of saving a default.
-- Safe to re-run, including on databases initialized from the current 01-schema.sql.
SET @height_cm_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'height_cm'
    ),
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN height_cm DECIMAL(4,1) NULL AFTER avatar_url'
);
PREPARE height_cm_migration FROM @height_cm_ddl;
EXECUTE height_cm_migration;
DEALLOCATE PREPARE height_cm_migration;

SET @weight_kg_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'weight_kg'
    ),
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN weight_kg DECIMAL(4,1) NULL AFTER height_cm'
);
PREPARE weight_kg_migration FROM @weight_kg_ddl;
EXECUTE weight_kg_migration;
DEALLOCATE PREPARE weight_kg_migration;
