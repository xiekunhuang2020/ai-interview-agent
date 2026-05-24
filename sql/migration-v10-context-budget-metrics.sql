-- v10: 记录 Prompt/RAG 上下文预算指标
ALTER TABLE ai_model_call_log
    ADD COLUMN prompt_chars INT DEFAULT NULL COMMENT '最终Prompt字符数' AFTER total_tokens,
    ADD COLUMN context_clipped TINYINT DEFAULT NULL COMMENT '上下文是否被裁剪 0-否 1-是' AFTER prompt_chars,
    ADD COLUMN clipped_chars INT DEFAULT NULL COMMENT '被裁剪字符数' AFTER context_clipped;
