USE hqc_plt;

-- One row per user and calendar day keeps the metric unique and inexpensive to query.
CREATE TABLE IF NOT EXISTS daily_online_users (
    user_id BIGINT UNSIGNED NOT NULL,
    online_date DATE NOT NULL,
    first_seen_at DATETIME(3) NOT NULL,
    PRIMARY KEY (online_date, user_id),
    KEY idx_daily_online_users_user (user_id, online_date),
    CONSTRAINT fk_daily_online_users_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
