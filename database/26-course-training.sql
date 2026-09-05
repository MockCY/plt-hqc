USE hqc_plt;

DROP PROCEDURE IF EXISTS migrate_course_training;
DELIMITER //
CREATE PROCEDURE migrate_course_training()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'courses' AND column_name = 'introduction') THEN
        ALTER TABLE courses ADD COLUMN introduction TEXT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'courses' AND column_name = 'audience') THEN
        ALTER TABLE courses ADD COLUMN audience TEXT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'course_exercises' AND column_name = 'training_sets') THEN
        ALTER TABLE course_exercises ADD COLUMN training_sets JSON NULL;
    END IF;
END//
DELIMITER ;
CALL migrate_course_training();
DROP PROCEDURE migrate_course_training;
