-- For existing databases created before Agent conversation audit support.
USE ai_interview;

CREATE TABLE IF NOT EXISTS agent_conversation_message (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    conversation_id     VARCHAR(128)    NOT NULL COMMENT 'Agent会话ID',
    turn_id             VARCHAR(64)     NOT NULL COMMENT '单轮对话ID',
    trace_id            VARCHAR(64)     DEFAULT NULL COMMENT 'HTTP请求追踪ID',
    agent_name          VARCHAR(128)    NOT NULL COMMENT 'Agent名称',
    role                VARCHAR(32)     NOT NULL COMMENT '消息角色 USER/ASSISTANT',
    message_content     MEDIUMTEXT      DEFAULT NULL COMMENT '消息内容',
    success             TINYINT         NOT NULL DEFAULT 1 COMMENT '是否成功 0-失败 1-成功',
    latency_ms          BIGINT          NOT NULL DEFAULT 0 COMMENT '助手侧响应耗时毫秒',
    error_message       VARCHAR(1024)   DEFAULT NULL COMMENT '错误信息',
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation_create_time (conversation_id, create_time),
    INDEX idx_trace_id (trace_id),
    INDEX idx_turn_id (turn_id),
    INDEX idx_success_create_time (success, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent对话消息审计表';
