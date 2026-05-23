USE ai_interview;
SET NAMES utf8mb4;

-- 模型调用审计增加标准化错误类型，便于看板按类型聚合失败原因。
ALTER TABLE ai_model_call_log
    ADD COLUMN error_type VARCHAR(64) DEFAULT NULL COMMENT '错误类型'
        AFTER error_message;

UPDATE ai_model_call_log
SET error_type = 'UNKNOWN'
WHERE success = 0 AND error_type IS NULL;

ALTER TABLE ai_model_call_log
    ADD INDEX idx_error_type_create_time (error_type, create_time);
