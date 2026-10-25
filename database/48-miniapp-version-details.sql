-- 先执行 47-miniapp-settings.sql。新增两个可空字段，保留当前版本及全部原配置。
-- 新库同样按 47、48 顺序执行；重跑不会清空或覆盖管理员配置。
SET NAMES utf8mb4;

SET @miniapp_settings_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'miniapp_settings' AND column_name = 'version_number'),
    'SELECT 1',
    'ALTER TABLE miniapp_settings ADD COLUMN version_number VARCHAR(32) NULL'
);
PREPARE miniapp_settings_migration FROM @miniapp_settings_ddl;
EXECUTE miniapp_settings_migration;
DEALLOCATE PREPARE miniapp_settings_migration;

SET @miniapp_settings_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'miniapp_settings' AND column_name = 'update_content'),
    'SELECT 1',
    'ALTER TABLE miniapp_settings ADD COLUMN update_content TEXT NULL'
);
PREPARE miniapp_settings_migration FROM @miniapp_settings_ddl;
EXECUTE miniapp_settings_migration;
DEALLOCATE PREPARE miniapp_settings_migration;
