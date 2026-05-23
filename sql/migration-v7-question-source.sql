USE ai_interview;
SET NAMES utf8mb4;

-- 面试题增加来源字段，用于区分当前简历事实和相似简历参考。
ALTER TABLE interview_question
    ADD COLUMN evidence_source VARCHAR(64) NOT NULL DEFAULT 'CURRENT_RESUME_FACT'
        COMMENT '问题依据来源 CURRENT_RESUME_FACT/SIMILAR_RESUME_REFERENCE'
        AFTER question_text;

ALTER TABLE interview_question
    ADD COLUMN source_note VARCHAR(500) DEFAULT NULL
        COMMENT '问题来源说明'
        AFTER evidence_source;
