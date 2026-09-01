USE hqc_plt;

-- Add category-owned model and serial-number rules.
ALTER TABLE device_categories
    ADD COLUMN device_model VARCHAR(100) NULL AFTER name,
    ADD COLUMN sn_prefix VARCHAR(12) NULL AFTER device_model;

UPDATE device_categories
SET device_model = CASE name
        WHEN '核心床' THEN 'Arvello 核心床'
        WHEN '普拉提床' THEN 'Arvello 普拉提床'
        WHEN '力量器械' THEN 'Arvello 力量器械'
        WHEN '有氧器械' THEN 'Arvello 有氧器械'
        WHEN '训练配件' THEN 'Arvello 训练配件'
        WHEN '徒手训练' THEN '徒手训练'
        ELSE CONCAT('Arvello ', name)
    END,
    sn_prefix = CASE name
        WHEN '核心床' THEN 'CORE'
        WHEN '普拉提床' THEN 'PILATES'
        WHEN '力量器械' THEN 'STRENGTH'
        WHEN '有氧器械' THEN 'CARDIO'
        WHEN '训练配件' THEN 'ACCESSORY'
        WHEN '徒手训练' THEN 'BODY'
        ELSE CONCAT('DEV', id)
    END
WHERE device_model IS NULL OR sn_prefix IS NULL;

ALTER TABLE device_categories
    MODIFY device_model VARCHAR(100) NOT NULL,
    MODIFY sn_prefix VARCHAR(12) NOT NULL,
    ADD UNIQUE KEY uk_device_categories_sn_prefix (sn_prefix);

-- The QR contains this opaque token. Existing serial numbers remain valid.
ALTER TABLE devices ADD COLUMN qr_token VARCHAR(64) NULL AFTER serial_number;
UPDATE devices SET qr_token = REPLACE(UUID(), '-', '') WHERE qr_token IS NULL;
ALTER TABLE devices
    MODIFY qr_token VARCHAR(64) NOT NULL,
    ADD UNIQUE KEY uk_devices_qr_token (qr_token);
