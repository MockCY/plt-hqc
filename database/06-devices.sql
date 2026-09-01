USE hqc_plt;

CREATE TABLE IF NOT EXISTS device_categories (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    device_model VARCHAR(100) NOT NULL,
    sn_prefix VARCHAR(12) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_categories_name (name),
    UNIQUE KEY uk_device_categories_sn_prefix (sn_prefix),
    KEY idx_device_categories_sort (sort_order, id)
) ENGINE=InnoDB;

INSERT INTO device_categories(name, device_model, sn_prefix, sort_order) VALUES
    ('核心床', 'Arvello 核心床', 'CORE', 10),
    ('普拉提床', 'Arvello 普拉提床', 'PILATES', 20),
    ('力量器械', 'Arvello 力量器械', 'STRENGTH', 30),
    ('有氧器械', 'Arvello 有氧器械', 'CARDIO', 40),
    ('训练配件', 'Arvello 训练配件', 'ACCESSORY', 50),
    ('徒手训练', '徒手训练', 'BODY', 60)
ON DUPLICATE KEY UPDATE device_model=VALUES(device_model), sn_prefix=VALUES(sn_prefix), sort_order=VALUES(sort_order);

CREATE TABLE IF NOT EXISTS devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(80) NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT '核心床',
    serial_number VARCHAR(64) NOT NULL,
    qr_token VARCHAR(64) NOT NULL,
    device_model VARCHAR(100) NOT NULL,
    bed_type VARCHAR(80) NOT NULL,
    spring_config VARCHAR(200) NOT NULL,
    purchased_on DATE NULL,
    connected BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_devices_code (code), UNIQUE KEY uk_devices_serial_number (serial_number), UNIQUE KEY uk_devices_qr_token (qr_token), KEY idx_devices_category_active (category, active), KEY idx_devices_active_sort (active, sort_order),
    CONSTRAINT fk_devices_category FOREIGN KEY (category) REFERENCES device_categories(name) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_device_selections (
    user_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    selected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id), UNIQUE KEY uk_user_device_device (device_id),
    CONSTRAINT fk_user_device_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_device_device FOREIGN KEY (device_id) REFERENCES devices(id)
) ENGINE=InnoDB;

INSERT INTO devices(code, name, category, serial_number, qr_token, device_model, bed_type, spring_config, purchased_on, connected, active, sort_order) VALUES
    ('FLEX_AIR_HOME', 'Arvello 柔力核心床', '核心床', 'ARV240428MNT001', REPLACE(UUID(), '-', ''), 'Arvello 柔力核心床', '标准款 · Mint', '轻弹簧 / 中弹簧 / 重弹簧', '2024-04-28', TRUE, TRUE, 10),
    ('FLEX_AIR_STUDIO', 'Arvello Studio 核心床', '核心床', 'ARV240428STD002', REPLACE(UUID(), '-', ''), 'Arvello Studio 核心床', '专业款 · Sage', '轻弹簧 / 中弹簧 / 重弹簧', '2024-04-28', FALSE, TRUE, 20),
    ('BODYWEIGHT', '徒手训练', '徒手训练', 'ARV-BODYWEIGHT-001', REPLACE(UUID(), '-', ''), '徒手训练', '不适用', '不适用', NULL, TRUE, TRUE, 30)
ON DUPLICATE KEY UPDATE name=VALUES(name), category=VALUES(category), serial_number=VALUES(serial_number), device_model=VALUES(device_model), bed_type=VALUES(bed_type), spring_config=VALUES(spring_config), purchased_on=VALUES(purchased_on), active=VALUES(active), sort_order=VALUES(sort_order);
