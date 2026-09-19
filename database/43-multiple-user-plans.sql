-- Allow one user to participate in multiple plans at the same time.
-- Existing selections and their timestamps are preserved.
-- Stop older backend versions before applying because they assume one row per user.
ALTER TABLE user_plan_selections
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (user_id, plan_id);

CREATE INDEX idx_training_visit_user_type_item_date
    ON training_detail_visits(user_id, detail_type, item_id, visited_at);
