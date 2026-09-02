USE hqc_plt;

ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS video_url VARCHAR(500) NULL AFTER cover_image,
    ADD COLUMN IF NOT EXISTS video_cover_image VARCHAR(500) NULL AFTER video_url,
    ADD COLUMN IF NOT EXISTS video_duration_seconds INT NULL AFTER video_cover_image;

UPDATE courses
SET cover_image = REPLACE(cover_image, '/static/fitness/', '/api/media/files/images/fitness/')
WHERE cover_image LIKE '/static/fitness/%';
