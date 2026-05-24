-- 记录模型官方 token usage，用于后续成本观测。
ALTER TABLE ai_model_call_log
    ADD COLUMN model_name VARCHAR(128) DEFAULT NULL COMMENT '模型名称' AFTER prompt_version,
    ADD COLUMN input_tokens INT DEFAULT NULL COMMENT '输入token数' AFTER latency_ms,
    ADD COLUMN output_tokens INT DEFAULT NULL COMMENT '输出token数' AFTER input_tokens,
    ADD COLUMN total_tokens INT DEFAULT NULL COMMENT '总token数' AFTER output_tokens,
    ADD INDEX idx_model_name_create_time (model_name, create_time);
