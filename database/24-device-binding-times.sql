USE hqc_plt;

ALTER TABLE devices
    ADD COLUMN last_bound_at DATETIME(3) NULL,
    ADD COLUMN last_unbound_at DATETIME(3) NULL;

UPDATE devices d JOIN user_device_selections s ON s.device_id = d.id
SET d.last_bound_at = s.selected_at;
