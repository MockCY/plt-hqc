-- 新库和已有库均执行，重跑不会覆盖管理员配置。
CREATE TABLE IF NOT EXISTS miniapp_settings (
 id TINYINT NOT NULL PRIMARY KEY,
 app_version VARCHAR(32) NULL COMMENT '展示版本，空表示未配置',
 revision BIGINT NOT NULL DEFAULT 0 COMMENT '并发更新版本'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO miniapp_settings (id, app_version, revision) VALUES (1, NULL, 0);
