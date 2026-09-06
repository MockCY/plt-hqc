CREATE TABLE IF NOT EXISTS training_activity (
    user_id BIGINT UNSIGNED NOT NULL,
    activity_type VARCHAR(20) NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    started_at DATETIME(3) NOT NULL,
    training_date DATE NOT NULL,
    active_seconds INT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, activity_type, item_id, started_at, training_date),
    CONSTRAINT fk_training_activity_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
