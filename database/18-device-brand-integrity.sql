USE hqc_plt;
SET NAMES utf8mb4;

-- Repair own devices created by an older application instance that did not persist the selected brand.
UPDATE devices
SET brand = CASE
    WHEN upper(serial_number) LIKE 'AVW%' THEN 'Manhart'
    ELSE 'ARVELLO'
END
WHERE device_source = 'OWN' AND (brand IS NULL OR trim(brand) = '');

DROP PROCEDURE IF EXISTS migrate_device_brand_integrity;
DELIMITER $$
CREATE PROCEDURE migrate_device_brand_integrity()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'devices'
          AND constraint_name = 'chk_devices_brand_source'
    ) THEN
        ALTER TABLE devices ADD CONSTRAINT chk_devices_brand_source CHECK (
            (device_source = 'OWN' AND brand IN ('Manhart', 'ARVELLO'))
            OR (device_source = 'THIRD_PARTY' AND brand IS NULL)
        );
    END IF;
END$$
DELIMITER ;
CALL migrate_device_brand_integrity();
DROP PROCEDURE migrate_device_brand_integrity;
