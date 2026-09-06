USE hqc_plt;

DROP PROCEDURE IF EXISTS migrate_course_training_tags;
DELIMITER //
CREATE PROCEDURE migrate_course_training_tags()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'courses' AND column_name = 'training_tags') THEN
        ALTER TABLE courses ADD COLUMN training_tags VARCHAR(200) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'course_exercises' AND column_name = 'recommended_plays') THEN
        ALTER TABLE course_exercises ADD COLUMN recommended_plays INT NULL;
    END IF;
END//
DELIMITER ;
CALL migrate_course_training_tags();
DROP PROCEDURE migrate_course_training_tags;
