USE hqc_plt;

CREATE TABLE device_models (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    sn_prefix VARCHAR(12) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_models_name (name),
    UNIQUE KEY uk_device_models_sn_prefix (sn_prefix)
) ENGINE=InnoDB;

INSERT INTO device_models(id, name, sn_prefix, created_at, updated_at)
SELECT id, device_model, sn_prefix, created_at, updated_at
FROM device_categories;

-- A model is now the complete device type, so normalize existing rows to the
-- model previously configured for their category before removing category.
UPDATE devices d
JOIN device_categories c ON c.name = d.category
SET d.device_model = c.device_model;

ALTER TABLE devices
    DROP FOREIGN KEY fk_devices_category,
    DROP INDEX uk_devices_code,
    DROP INDEX idx_devices_category_active,
    DROP INDEX idx_devices_active_sort,
    DROP COLUMN code,
    DROP COLUMN name,
    DROP COLUMN category,
    DROP COLUMN bed_type,
    DROP COLUMN spring_config,
    DROP COLUMN purchased_on,
    DROP COLUMN connected,
    DROP COLUMN active,
    DROP COLUMN sort_order,
    ADD KEY idx_devices_device_model (device_model),
    ADD CONSTRAINT fk_devices_device_model FOREIGN KEY (device_model) REFERENCES device_models(name)
        ON UPDATE CASCADE ON DELETE RESTRICT;

DROP TABLE device_categories;
