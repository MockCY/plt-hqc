USE hqc_plt;

-- One-time upgrade for databases created before device categories.
ALTER TABLE devices
    ADD COLUMN category VARCHAR(30) NULL AFTER name;

UPDATE devices
SET category = CASE
        WHEN code = 'BODYWEIGHT' THEN '徒手训练'
        ELSE '核心床'
    END
WHERE category IS NULL OR category = '';

ALTER TABLE devices
    MODIFY category VARCHAR(30) NOT NULL DEFAULT '核心床',
    ADD KEY idx_devices_category_active (category, active);
