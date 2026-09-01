USE hqc_plt;

DROP PROCEDURE IF EXISTS migrate_course_detail;
DELIMITER //
CREATE PROCEDURE migrate_course_detail()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'courses' AND column_name = 'view_count') THEN
        ALTER TABLE courses ADD COLUMN view_count BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER video_duration_seconds;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'exercises' AND column_name = 'cover_image') THEN
        ALTER TABLE exercises ADD COLUMN cover_image VARCHAR(500) NULL AFTER safety_tip;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'exercises' AND column_name = 'video_url') THEN
        ALTER TABLE exercises ADD COLUMN video_url VARCHAR(500) NULL AFTER cover_image;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'exercises' AND column_name = 'video_cover_image') THEN
        ALTER TABLE exercises ADD COLUMN video_cover_image VARCHAR(500) NULL AFTER video_url;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'exercises' AND column_name = 'video_duration_seconds') THEN
        ALTER TABLE exercises ADD COLUMN video_duration_seconds INT NULL AFTER video_cover_image;
    END IF;
END//
DELIMITER ;

CALL migrate_course_detail();
DROP PROCEDURE migrate_course_detail;

CREATE TABLE IF NOT EXISTS course_views (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    course_id BIGINT UNSIGNED NOT NULL,
    visitor_key VARCHAR(80) NOT NULL,
    viewed_on DATE NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_views_daily (course_id, visitor_key, viewed_on),
    KEY idx_course_views_date (viewed_on),
    CONSTRAINT fk_course_views_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB;

UPDATE courses SET view_count = 14000 WHERE id = 1 AND view_count = 0;
UPDATE courses SET view_count = 8600 WHERE id = 2 AND view_count = 0;
UPDATE courses SET view_count = 5200 WHERE id = 3 AND view_count = 0;

UPDATE exercises SET cover_image = '/media/images/fitness/course-fullbody.jpg' WHERE id = 1 AND cover_image IS NULL;
UPDATE exercises SET cover_image = '/media/images/fitness/course-core.jpg' WHERE id = 2 AND cover_image IS NULL;
UPDATE exercises SET cover_image = '/media/images/fitness/course-stretch.jpg' WHERE id = 3 AND cover_image IS NULL;
