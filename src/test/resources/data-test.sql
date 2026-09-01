INSERT INTO users(id, openid, phone, nickname, status) VALUES
    (1, 'seed-user-openid', '13800000000', '测试用户', 'ACTIVE');

INSERT INTO courses(id, title, type, duration_minutes, level, equipment, summary, cover_image, status, sort_order) VALUES
    (1, '全身激活', '全身', 12, '初级', '无器械', '热身、基础动作与拉伸', '/static/fitness/course-fullbody.jpg', 'PUBLISHED', 10),
    (2, '核心基础', '核心', 8, '初级', '无器械', '建立腹部控制', '/static/fitness/course-core.jpg', 'PUBLISHED', 20),
    (3, '肩背舒展', '拉伸', 6, '放松', '无器械', '释放肩颈紧张', '/static/fitness/course-stretch.jpg', 'PUBLISHED', 30);

INSERT INTO device_categories(id, name, sort_order) VALUES
    (1, '核心床', 10), (2, '普拉提床', 20), (3, '力量器械', 30),
    (4, '有氧器械', 40), (5, '训练配件', 50), (6, '徒手训练', 60);

INSERT INTO devices(id, code, name, category, serial_number, device_model, bed_type, spring_config, purchased_on, connected, active, sort_order) VALUES
    (1, 'FLEX_AIR_HOME', 'Arvello 柔力核心床', '核心床', 'ARV240428MNT001', 'Arvello 柔力核心床', '标准款 · Mint', '轻弹簧 / 中弹簧 / 重弹簧', '2024-04-28', TRUE, TRUE, 10),
    (2, 'FLEX_AIR_STUDIO', 'Arvello Studio 核心床', '核心床', 'ARV240428STD002', 'Arvello Studio 核心床', '专业款 · Sage', '轻弹簧 / 中弹簧 / 重弹簧', '2024-04-28', FALSE, TRUE, 20),
    (3, 'BODYWEIGHT', '徒手训练', '徒手训练', 'ARV-BODYWEIGHT-001', '徒手训练', '不适用', '不适用', NULL, TRUE, TRUE, 30);

INSERT INTO exercises(id, name, body_part, level, equipment, suggested_sets, target, cue, safety_tip, status, sort_order) VALUES
    (1, '徒手深蹲', '下肢', '基础', '无器械', 2, '10 次', '双脚与肩同宽。', '减小幅度并放慢速度。', 'PUBLISHED', 10),
    (2, '站姿提膝', '核心', '基础', '无器械', 2, '30 秒', '收紧腹部。', '不要憋气。', 'PUBLISHED', 20),
    (3, '肩背放松', '肩背', '拉伸', '无器械', 2, '20 秒', '肩膀自然下沉。', '避免快速甩动。', 'PUBLISHED', 30);

INSERT INTO course_exercises(course_id, exercise_id, sort_order) VALUES
    (1, 1, 10), (1, 2, 20), (1, 3, 30), (2, 2, 10), (3, 3, 10);

INSERT INTO training_plans(id, title, week_number, sessions_per_week, description, active, sort_order)
VALUES (1, '新手训练计划', 1, 3, '每周三次短训练。', TRUE, 10);

INSERT INTO training_plan_items(id, plan_id, course_id, day_offset, sort_order) VALUES
    (1, 1, 1, 2, 10), (2, 1, 2, 4, 20), (3, 1, 3, 6, 30);

INSERT INTO training_plans(id, title, week_number, sessions_per_week, description, active, sort_order) VALUES
    (2, '核心稳定计划', 1, 3, '每周三次核心训练。', TRUE, 20),
    (3, '舒展恢复计划', 1, 2, '每周两次恢复训练。', TRUE, 30);

INSERT INTO training_plan_items(id, plan_id, course_id, day_offset, sort_order) VALUES
    (4, 2, 2, 1, 10), (5, 2, 1, 3, 20), (6, 2, 2, 5, 30),
    (7, 3, 3, 2, 10), (8, 3, 3, 5, 20);

INSERT INTO campaigns(id, code, title, rules_text, status, sort_order) VALUES
    (1, 'AUGUST_ABS', '8月马甲线训练营', '完成当日任意一节训练后即可打卡\n每天最多记录一次', 'PUBLISHED', 10);
