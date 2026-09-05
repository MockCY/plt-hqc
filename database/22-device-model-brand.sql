-- Device serial numbers and existing sequence counters are never rewritten.
ALTER TABLE device_models
    ADD COLUMN brand VARCHAR(30) NOT NULL DEFAULT 'ARVELLO' AFTER name;

UPDATE device_models
SET brand = 'Manhart', sn_prefix = CONCAT('MN', SUBSTRING(sn_prefix, 3))
WHERE sn_prefix IN ('AVW01', 'AVW02', 'AVW03');
