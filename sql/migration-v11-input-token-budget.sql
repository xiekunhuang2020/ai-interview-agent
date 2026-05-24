-- v11: 记录单次模型调用输入 Token 预算和超预算状态
ALTER TABLE ai_model_call_log
    ADD COLUMN input_token_budget INT DEFAULT NULL COMMENT '输入token目标预算' AFTER total_tokens,
    ADD COLUMN input_token_over_budget INT DEFAULT NULL COMMENT '输入token超预算数量' AFTER input_token_budget,
    ADD COLUMN budget_exceeded TINYINT DEFAULT NULL COMMENT '输入token是否超预算 0-否 1-是' AFTER input_token_over_budget,
    ADD COLUMN budget_uncovered TINYINT DEFAULT NULL COMMENT '超预算但未裁剪 0-否 1-是' AFTER budget_exceeded;
