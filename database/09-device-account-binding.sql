USE hqc_plt;

-- One-time upgrade for databases created before account-bound devices.
ALTER TABLE devices
    ADD COLUMN serial_number VARCHAR(64) NULL AFTER name,
    ADD COLUMN device_model VARCHAR(100) NULL AFTER serial_number,
    ADD COLUMN bed_type VARCHAR(80) NULL AFTER device_model,
    ADD COLUMN spring_config VARCHAR(200) NULL AFTER bed_type,
    ADD COLUMN purchased_on DATE NULL AFTER spring_config;

UPDATE devices
SET serial_number = CASE code
        WHEN 'FLEX_AIR_HOME' THEN 'ARV240428MNT001'
        WHEN 'FLEX_AIR_STUDIO' THEN 'ARV240428STD002'
        WHEN 'BODYWEIGHT' THEN 'ARV-BODYWEIGHT-001'
        ELSE CONCAT('ARV-', UPPER(code))
    END,
    device_model = CASE code
        WHEN 'FLEX_AIR_HOME' THEN 'Arvello 柔力核心床'
        WHEN 'FLEX_AIR_STUDIO' THEN 'Arvello Studio 核心床'
        ELSE name
    END,
    bed_type = CASE code
        WHEN 'FLEX_AIR_HOME' THEN '标准款 · Mint'
        WHEN 'FLEX_AIR_STUDIO' THEN '专业款 · Sage'
        ELSE '不适用'
    END,
    spring_config = CASE
        WHEN code IN ('FLEX_AIR_HOME', 'FLEX_AIR_STUDIO') THEN '轻弹簧 / 中弹簧 / 重弹簧'
        ELSE '不适用'
    END,
    purchased_on = CASE
        WHEN code IN ('FLEX_AIR_HOME', 'FLEX_AIR_STUDIO') THEN '2024-04-28'
        ELSE NULL
    END
WHERE serial_number IS NULL;

ALTER TABLE devices
    MODIFY serial_number VARCHAR(64) NOT NULL,
    MODIFY device_model VARCHAR(100) NOT NULL,
    MODIFY bed_type VARCHAR(80) NOT NULL,
    MODIFY spring_config VARCHAR(200) NOT NULL,
    ADD UNIQUE KEY uk_devices_serial_number (serial_number);

-- This statement intentionally fails if historical duplicate bindings exist.
-- Audit duplicates first with:
-- SELECT device_id, COUNT(*) FROM user_device_selections GROUP BY device_id HAVING COUNT(*) > 1;
ALTER TABLE user_device_selections
    DROP INDEX idx_user_device_device,
    ADD UNIQUE KEY uk_user_device_device (device_id);
