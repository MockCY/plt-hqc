ALTER TABLE exercises
    ADD COLUMN focus_image_url VARCHAR(500) NULL,
    ADD COLUMN focus_parts VARCHAR(100) NULL,
    ADD COLUMN spring_sets JSON NULL,
    ADD COLUMN key_points TEXT NULL,
    ADD COLUMN common_mistakes TEXT NULL,
    ADD COLUMN instruction_audio_url VARCHAR(500) NULL;
