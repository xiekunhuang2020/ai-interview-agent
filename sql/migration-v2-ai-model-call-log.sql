-- For existing databases created before fallback/metrics support.
USE ai_interview;

ALTER TABLE ai_model_call_log
    ADD COLUMN fallback_used TINYINT NOT NULL DEFAULT 0 COMMENT '是否使用降级结果 0-否 1-是' AFTER success;

ALTER TABLE ai_model_call_log
    ADD INDEX idx_prompt_version_create_time (prompt_version, create_time);
