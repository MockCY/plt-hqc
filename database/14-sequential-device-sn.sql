USE hqc_plt;

-- Reserve serial numbers per model under a row lock. Existing devices are kept unchanged.
ALTER TABLE device_models
    ADD COLUMN next_serial_sequence BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER sn_prefix;

-- Preserve continuity if any devices already use the new SN format.
UPDATE device_models m
SET m.next_serial_sequence = COALESCE((
    SELECT MAX(CAST(SUBSTRING(d.serial_number, CHAR_LENGTH(m.sn_prefix) + 5, 5) AS UNSIGNED))
    FROM devices d
    WHERE d.device_model = m.name
      AND d.serial_number REGEXP CONCAT('^', m.sn_prefix, 'K37A[0-9]{5}X8M$')
), 0);
