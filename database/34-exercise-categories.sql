USE hqc_plt;

-- Review unrecognized values before and after migration. These actions remain visible
-- under All; an administrator must select one of the four types when editing them.
SELECT id, name, body_part
FROM exercises
WHERE TRIM(body_part) NOT IN (
    '核心训练', '核心', '臀腿塑形', '臀腿', '下肢',
    '肩背体态', '肩背', '拉伸协调', '全身', '拉伸'
)
ORDER BY id;

-- Idempotent: update known categories only; preserve IDs, course links and unknown values.
UPDATE exercises
SET body_part = CASE
    WHEN TRIM(body_part) IN ('核心训练', '核心') THEN '核心训练'
    WHEN TRIM(body_part) IN ('臀腿塑形', '臀腿', '下肢') THEN '臀腿塑形'
    WHEN TRIM(body_part) IN ('肩背体态', '肩背') THEN '肩背体态'
    WHEN TRIM(body_part) IN ('拉伸协调', '全身', '拉伸') THEN '拉伸协调'
    ELSE body_part
END
WHERE body_part IN ('核心', '臀腿', '下肢', '肩背', '全身', '拉伸')
   OR (OCTET_LENGTH(body_part) <> OCTET_LENGTH(TRIM(body_part)) AND TRIM(body_part) IN (
       '核心训练', '核心', '臀腿塑形', '臀腿', '下肢',
       '肩背体态', '肩背', '拉伸协调', '全身', '拉伸'
   ));

SELECT body_part, COUNT(*) AS exercise_count
FROM exercises
GROUP BY body_part
ORDER BY body_part;
