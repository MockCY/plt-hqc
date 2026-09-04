USE hqc_plt;
SET NAMES utf8mb4;

-- Add device brand, display name, and source. Existing devices are first-party ARVELLO devices.
DROP PROCEDURE IF EXISTS migrate_device_brand_source;
DELIMITER $$
CREATE PROCEDURE migrate_device_brand_source()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'devices' AND column_name = 'brand') THEN
        ALTER TABLE devices ADD COLUMN brand VARCHAR(32) NULL AFTER device_model;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'devices' AND column_name = 'device_name') THEN
        ALTER TABLE devices ADD COLUMN device_name VARCHAR(100) NULL AFTER brand;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'devices' AND column_name = 'device_source') THEN
        ALTER TABLE devices ADD COLUMN device_source VARCHAR(20) NOT NULL DEFAULT 'OWN' AFTER device_name;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE() AND table_name = 'devices' AND constraint_name = 'fk_devices_device_model') THEN
        ALTER TABLE devices DROP FOREIGN KEY fk_devices_device_model;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'devices' AND index_name = 'idx_devices_source') THEN
        ALTER TABLE devices ADD KEY idx_devices_source (device_source);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'devices' AND index_name = 'idx_devices_brand') THEN
        ALTER TABLE devices ADD KEY idx_devices_brand (brand);
    END IF;
END$$
DELIMITER ;
CALL migrate_device_brand_source();
DROP PROCEDURE migrate_device_brand_source;

UPDATE devices
SET brand = COALESCE(brand, 'ARVELLO'),
    device_name = COALESCE(device_name, device_model),
    device_source = COALESCE(device_source, 'OWN')
WHERE device_source IS NULL OR device_source = 'OWN';
