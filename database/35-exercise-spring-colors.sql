USE hqc_plt;

-- Add color counts without assigning a color to any legacy spring_sets value.
-- Safe to re-run, including on databases initialized from the current 01-schema.sql.
SET @spring_counts_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'exercises' AND column_name = 'spring_counts'
    ),
    'SELECT 1',
    'ALTER TABLE exercises ADD COLUMN spring_counts JSON NULL AFTER spring_sets'
);
PREPARE spring_counts_migration FROM @spring_counts_ddl;
EXECUTE spring_counts_migration;
DEALLOCATE PREPARE spring_counts_migration;
