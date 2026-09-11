USE hqc_plt;

INSERT INTO courses(id, title, type, duration_minutes, level, equipment, summary, cover_image, view_count, status, sort_order)
VALUES
    (1, '全身激活', '全身', 12, '初级', '无器械', '热身、3 个基础动作与舒缓拉伸', '/api/media/files/images/fitness/course-fullbody.jpg', 14000, 'PUBLISHED', 10),
    (2, '核心基础', '核心', 8, '初级', '无器械', '建立腹部控制，改善身体稳定性', '/api/media/files/images/fitness/course-core.jpg', 8600, 'PUBLISHED', 20),
    (3, '肩背舒展', '拉伸', 6, '放松', '无器械', '释放肩颈紧张，缓解久坐疲劳', '/api/media/files/images/fitness/course-stretch.jpg', 5200, 'PUBLISHED', 30)
ON DUPLICATE KEY UPDATE
    title = VALUES(title), type = VALUES(type), duration_minutes = VALUES(duration_minutes),
    level = VALUES(level), equipment = VALUES(equipment), summary = VALUES(summary),
    cover_image = VALUES(cover_image), view_count = VALUES(view_count), status = VALUES(status), sort_order = VALUES(sort_order);

INSERT INTO exercises(id, name, body_part, level, equipment, suggested_sets, target, cue, safety_tip, cover_image, status, sort_order)
VALUES
    (1, '徒手深蹲', '臀腿塑形', '基础', '无器械', 2, '10 次', '双脚与肩同宽，髋部向后下方坐，起身时脚掌稳定踩地。', '膝盖不适时减小下蹲幅度，并放慢速度。', '/api/media/files/images/fitness/course-fullbody.jpg', 'PUBLISHED', 10),
    (2, '站姿提膝', '核心训练', '基础', '无器械', 2, '30 秒', '收紧腹部，交替抬膝至舒适高度，保持上身稳定。', '不要憋气，腰部不适时降低抬膝高度。', '/api/media/files/images/fitness/course-core.jpg', 'PUBLISHED', 20),
    (3, '肩背放松', '肩背体态', '挑战', '无器械', 2, '20 秒', '肩膀自然下沉，手臂横过胸前，轻柔拉伸肩背。', '保持动作轻柔，不要快速甩动或强行拉伸。', '/api/media/files/images/fitness/course-stretch.jpg', 'PUBLISHED', 30)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), body_part = VALUES(body_part), level = VALUES(level),
    equipment = VALUES(equipment), suggested_sets = VALUES(suggested_sets), target = VALUES(target),
    cue = VALUES(cue), safety_tip = VALUES(safety_tip), cover_image = VALUES(cover_image), status = VALUES(status), sort_order = VALUES(sort_order);

INSERT INTO course_exercises(course_id, exercise_id, sort_order, duration_seconds, target)
VALUES
    (1, 1, 10, 180, '10 次'), (1, 2, 20, 180, '30 秒'), (1, 3, 30, 180, '20 秒'),
    (2, 2, 10, 240, '30 秒'),
    (3, 3, 10, 240, '20 秒')
ON DUPLICATE KEY UPDATE
    sort_order = VALUES(sort_order), duration_seconds = VALUES(duration_seconds), target = VALUES(target);

INSERT INTO training_plans(id, title, week_number, sessions_per_week, description, active, sort_order)
VALUES (1, '新手训练计划', 1, 3, '每周三次短训练，先熟悉动作，再逐步增加时长。', TRUE, 10)
ON DUPLICATE KEY UPDATE
    title = VALUES(title), week_number = VALUES(week_number), sessions_per_week = VALUES(sessions_per_week),
    description = VALUES(description), active = VALUES(active), sort_order = VALUES(sort_order);

INSERT INTO training_plan_items(id, plan_id, course_id, day_offset, sort_order)
VALUES
    (1, 1, 1, 2, 10),
    (2, 1, 2, 4, 20),
    (3, 1, 3, 6, 30)
ON DUPLICATE KEY UPDATE
    plan_id = VALUES(plan_id), course_id = VALUES(course_id), day_offset = VALUES(day_offset), sort_order = VALUES(sort_order);
