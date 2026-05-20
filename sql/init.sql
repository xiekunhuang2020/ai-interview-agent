-- AI智能面试辅助系统数据库初始化脚本

CREATE DATABASE IF NOT EXISTS ai_interview
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ai_interview;

-- 简历信息表
CREATE TABLE IF NOT EXISTS resume_info (
    resume_id           VARCHAR(64)     PRIMARY KEY COMMENT '简历ID',
    original_file_name  VARCHAR(255)    DEFAULT NULL COMMENT '原始文件名',
    resume_text         MEDIUMTEXT      DEFAULT NULL COMMENT '简历文本内容',
    overall_score       INT             DEFAULT 0 COMMENT '综合评分',
    score_detail_json   TEXT            DEFAULT NULL COMMENT '评分详情JSON',
    strengths_json      TEXT            DEFAULT NULL COMMENT '优势列表JSON',
    suggestions_json    TEXT            DEFAULT NULL COMMENT '建议列表JSON',
    summary             TEXT            DEFAULT NULL COMMENT '综合总结',
    questions_json      MEDIUMTEXT      DEFAULT NULL COMMENT '面试问题JSON',
    evaluation_json     MEDIUMTEXT      DEFAULT NULL COMMENT '评估结果JSON',
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT         DEFAULT 0 COMMENT '逻辑删除标志 0-未删除 1-已删除',
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历信息表';

-- 面试流程状态表
CREATE TABLE IF NOT EXISTS interview_session (
    resume_id           VARCHAR(64)     PRIMARY KEY COMMENT '简历ID',
    original_file_name  VARCHAR(255)    DEFAULT NULL COMMENT '原始文件名',
    status              VARCHAR(64)     NOT NULL COMMENT '当前流程状态',
    current_stage       VARCHAR(64)     DEFAULT NULL COMMENT '当前处理阶段',
    failed_stage        VARCHAR(64)     DEFAULT NULL COMMENT '最近失败阶段',
    failed_reason       VARCHAR(500)    DEFAULT NULL COMMENT '最近失败原因',
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status_update_time (status, update_time),
    INDEX idx_current_stage_update_time (current_stage, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试流程状态表';

-- AI 模型调用审计表
CREATE TABLE IF NOT EXISTS ai_model_call_log (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    trace_id        VARCHAR(64)     DEFAULT NULL COMMENT '请求追踪ID',
    operation_name  VARCHAR(128)    NOT NULL COMMENT '模型调用场景',
    prompt_version  VARCHAR(128)    NOT NULL COMMENT 'Prompt版本',
    success         TINYINT         NOT NULL DEFAULT 0 COMMENT '是否成功 0-失败 1-成功',
    fallback_used   TINYINT         NOT NULL DEFAULT 0 COMMENT '是否使用降级结果 0-否 1-是',
    attempt_count   INT             NOT NULL DEFAULT 1 COMMENT '实际尝试次数',
    latency_ms      BIGINT          NOT NULL DEFAULT 0 COMMENT '总耗时毫秒',
    error_message   VARCHAR(1024)   DEFAULT NULL COMMENT '错误信息',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_trace_id (trace_id),
    INDEX idx_operation_create_time (operation_name, create_time),
    INDEX idx_prompt_version_create_time (prompt_version, create_time),
    INDEX idx_success_create_time (success, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型调用审计表';

-- Agent 对话消息审计表
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
