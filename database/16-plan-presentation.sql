USE hqc_plt;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_plan_presentation;
DELIMITER $$
CREATE PROCEDURE migrate_plan_presentation()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'subtitle') THEN
        ALTER TABLE training_plans ADD COLUMN subtitle VARCHAR(160) NULL AFTER description;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'cover_image') THEN
        ALTER TABLE training_plans ADD COLUMN cover_image VARCHAR(500) NULL AFTER subtitle;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'level') THEN
        ALTER TABLE training_plans ADD COLUMN level VARCHAR(30) NULL AFTER cover_image;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'training_scene') THEN
        ALTER TABLE training_plans ADD COLUMN training_scene VARCHAR(30) NULL AFTER level;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'session_minutes') THEN
        ALTER TABLE training_plans ADD COLUMN session_minutes INT NULL AFTER training_scene;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'benefit_one') THEN
        ALTER TABLE training_plans ADD COLUMN benefit_one VARCHAR(80) NULL AFTER session_minutes;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'benefit_two') THEN
        ALTER TABLE training_plans ADD COLUMN benefit_two VARCHAR(80) NULL AFTER benefit_one;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'training_plans' AND column_name = 'benefit_three') THEN
        ALTER TABLE training_plans ADD COLUMN benefit_three VARCHAR(80) NULL AFTER benefit_two;
    END IF;
END$$
DELIMITER ;
CALL migrate_plan_presentation();
DROP PROCEDURE migrate_plan_presentation;

UPDATE training_plans
SET subtitle = COALESCE(subtitle, CASE id
        WHEN 1 THEN '建立动作基础，养成运动习惯'
        WHEN 2 THEN '强化核心力量，提升身体稳定性'
        WHEN 3 THEN '改善圆肩驼背，缓解肩颈不适'
        ELSE '循序渐进，完成你的训练目标'
    END),
    cover_image = COALESCE(cover_image, CASE id
        WHEN 1 THEN '/api/media/files/images/flex-air/device-reformer-reference.png'
        WHEN 2 THEN '/api/media/files/images/flex-air/catalog-backbend.jpg'
        ELSE '/api/media/files/images/flex-air/studio-reformer.jpg'
    END),
    level = COALESCE(level, CASE id WHEN 2 THEN '进阶' WHEN 3 THEN '舒缓' ELSE '基础' END),
    training_scene = COALESCE(training_scene, '居家'),
    session_minutes = COALESCE(session_minutes, CASE id WHEN 1 THEN 15 ELSE 20 END),
    benefit_one = COALESCE(benefit_one, '核心激活'),
    benefit_two = COALESCE(benefit_two, '身体唤醒'),
    benefit_three = COALESCE(benefit_three, '训练习惯');
