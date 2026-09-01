USE hqc_plt;

-- One-time upgrade from fixed device category values to managed category data.
CREATE TABLE device_categories (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_categories_name (name),
    KEY idx_device_categories_sort (sort_order, id)
) ENGINE=InnoDB;

INSERT INTO device_categories(name, sort_order) VALUES
    ('核心床', 10), ('普拉提床', 20), ('力量器械', 30),
    ('有氧器械', 40), ('训练配件', 50), ('徒手训练', 60);

INSERT INTO device_categories(name, sort_order)
SELECT DISTINCT d.category, 100
FROM devices d
LEFT JOIN device_categories c ON c.name = d.category
WHERE c.id IS NULL;

ALTER TABLE devices
    ADD CONSTRAINT fk_devices_category
    FOREIGN KEY (category) REFERENCES device_categories(name)
    ON UPDATE CASCADE ON DELETE RESTRICT;
