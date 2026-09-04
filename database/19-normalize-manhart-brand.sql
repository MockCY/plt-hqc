USE hqc_plt;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS normalize_manhart_brand;
DELIMITER $$
CREATE PROCEDURE normalize_manhart_brand()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'devices'
          AND constraint_name = 'chk_devices_brand_source'
    ) THEN
        ALTER TABLE devices DROP CHECK chk_devices_brand_source;
    END IF;

    UPDATE devices
    SET brand = 'Manhart'
    WHERE lower(trim(brand)) = 'manhart';

    ALTER TABLE devices ADD CONSTRAINT chk_devices_brand_source CHECK (
        (device_source = 'OWN' AND brand IN ('Manhart', 'ARVELLO'))
        OR (device_source = 'THIRD_PARTY' AND brand IS NULL)
    );
END$$
DELIMITER ;
CALL normalize_manhart_brand();
DROP PROCEDURE normalize_manhart_brand;
