USE ai_interview;

-- 清理第 4 步拆表后不再使用的 resume_info 旧结果字段。
ALTER TABLE resume_info
    DROP COLUMN overall_score,
    DROP COLUMN score_detail_json,
    DROP COLUMN strengths_json,
    DROP COLUMN suggestions_json,
    DROP COLUMN summary,
    DROP COLUMN questions_json,
    DROP COLUMN evaluation_json;
