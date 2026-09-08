-- Keep legacy hashes for compatibility; keyless firmware does not read or write them.
ALTER TABLE sensor_devices
 MODIFY COLUMN key_hash CHAR(64) NULL,
 MODIFY COLUMN claim_hash CHAR(64) NULL;
