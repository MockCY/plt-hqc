USE hqc_plt;

DROP PROCEDURE IF EXISTS migrate_exercise_background_music;
DELIMITER //
CREATE PROCEDURE migrate_exercise_background_music()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'exercises' AND column_name = 'background_music_url') THEN
        ALTER TABLE exercises ADD COLUMN background_music_url VARCHAR(500) NULL AFTER video_duration_seconds;
    END IF;
END//
DELIMITER ;

CALL migrate_exercise_background_music();
DROP PROCEDURE migrate_exercise_background_music;
