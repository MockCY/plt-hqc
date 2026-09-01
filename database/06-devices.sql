USE hqc_plt;

CREATE TABLE IF NOT EXISTS device_models (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    sn_prefix VARCHAR(12) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_models_name (name),
    UNIQUE KEY uk_device_models_sn_prefix (sn_prefix)
) ENGINE=InnoDB;

INSERT INTO device_models(name, sn_prefix) VALUES
    ('Arvello 核心床', 'CORE'),
    ('Arvello 普拉提床', 'PILATES'),
    ('Arvello 力量器械', 'STRENGTH'),
    ('Arvello 有氧器械', 'CARDIO'),
    ('Arvello 训练配件', 'ACCESSORY'),
    ('徒手训练', 'BODY')
ON DUPLICATE KEY UPDATE sn_prefix=VALUES(sn_prefix);

CREATE TABLE IF NOT EXISTS devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    serial_number VARCHAR(64) NOT NULL,
    qr_token VARCHAR(64) NOT NULL,
    device_model VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_devices_serial_number (serial_number),
    UNIQUE KEY uk_devices_qr_token (qr_token),
    KEY idx_devices_device_model (device_model),
    CONSTRAINT fk_devices_device_model FOREIGN KEY (device_model) REFERENCES device_models(name)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_device_selections (
    user_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    selected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_device_device (device_id),
    CONSTRAINT fk_user_device_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_device_device FOREIGN KEY (device_id) REFERENCES devices(id)
) ENGINE=InnoDB;

INSERT INTO devices(serial_number, qr_token, device_model) VALUES
    ('ARV240428MNT001', REPLACE(UUID(), '-', ''), 'Arvello 核心床'),
    ('ARV240428STD002', REPLACE(UUID(), '-', ''), 'Arvello 核心床'),
    ('ARV-BODYWEIGHT-001', REPLACE(UUID(), '-', ''), '徒手训练')
ON DUPLICATE KEY UPDATE device_model=VALUES(device_model);
