USE hqc_plt;

CREATE TABLE IF NOT EXISTS user_presence_visits (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    client_id VARCHAR(160) NOT NULL,
    online_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    offline_at DATETIME(3) NULL,
    end_reason VARCHAR(20) NULL,
    active_client VARCHAR(160) GENERATED ALWAYS AS (IF(offline_at IS NULL, client_id, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_presence_active_client (user_id, active_client),
    KEY idx_presence_user_history (user_id, online_at, id),
    KEY idx_presence_expiry (offline_at, last_seen_at),
    CONSTRAINT fk_presence_visits_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
