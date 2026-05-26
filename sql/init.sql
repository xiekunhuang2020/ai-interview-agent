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

-- 简历评分拆分表
CREATE TABLE IF NOT EXISTS resume_score (
    resume_id           VARCHAR(64)     PRIMARY KEY COMMENT '简历ID',
    overall_score       INT             NOT NULL DEFAULT 0 COMMENT '综合评分',
    project_score       INT             NOT NULL DEFAULT 0 COMMENT '项目深度评分',
    skill_match_score   INT             NOT NULL DEFAULT 0 COMMENT '技能匹配评分',
    content_score       INT             NOT NULL DEFAULT 0 COMMENT '内容完整评分',
    structure_score     INT             NOT NULL DEFAULT 0 COMMENT '结构清晰评分',
    expression_score    INT             NOT NULL DEFAULT 0 COMMENT '表达质量评分',
    summary             TEXT            DEFAULT NULL COMMENT '综合总结',
    strengths_json      TEXT            DEFAULT NULL COMMENT '优势列表JSON',
    suggestions_json    TEXT            DEFAULT NULL COMMENT '建议列表JSON',
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_overall_score (overall_score),
    INDEX idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历评分拆分表';

-- 面试问题明细表
CREATE TABLE IF NOT EXISTS interview_question (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    resume_id       VARCHAR(64)     NOT NULL COMMENT '简历ID',
    question_index  INT             NOT NULL COMMENT '问题序号，从0开始',
    question_type   VARCHAR(64)     NOT NULL COMMENT '问题类型',
    category        VARCHAR(128)    NOT NULL COMMENT '问题分类',
    question_text   TEXT            NOT NULL COMMENT '问题内容',
    evidence_source VARCHAR(64)     NOT NULL DEFAULT 'CURRENT_RESUME_FACT' COMMENT '问题依据来源 CURRENT_RESUME_FACT/SIMILAR_RESUME_REFERENCE',
    source_note     VARCHAR(500)    DEFAULT NULL COMMENT '问题来源说明',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_resume_question_index (resume_id, question_index),
    INDEX idx_resume_type (resume_id, question_type),
    INDEX idx_question_type (question_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试问题明细表';

-- 面试评估拆分表
CREATE TABLE IF NOT EXISTS interview_evaluation (
    resume_id               VARCHAR(64)     PRIMARY KEY COMMENT '简历ID',
    session_id              VARCHAR(128)    DEFAULT NULL COMMENT '模型输出的面试会话ID',
    total_questions         INT             NOT NULL DEFAULT 0 COMMENT '总问题数',
    overall_score           INT             NOT NULL DEFAULT 0 COMMENT '复盘总分',
    overall_feedback        TEXT            DEFAULT NULL COMMENT '整体反馈',
    category_scores_json    TEXT            DEFAULT NULL COMMENT '分类得分JSON',
    question_details_json   MEDIUMTEXT      DEFAULT NULL COMMENT '问题评估明细JSON',
    strengths_json          TEXT            DEFAULT NULL COMMENT '优势表现JSON',
    improvements_json       TEXT            DEFAULT NULL COMMENT '提升建议JSON',
    reference_answers_json  MEDIUMTEXT      DEFAULT NULL COMMENT '参考答案JSON',
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_overall_score (overall_score),
    INDEX idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='面试评估拆分表';

-- 岗位匹配结果表
CREATE TABLE IF NOT EXISTS jd_match_result (
    resume_id                   VARCHAR(64)     PRIMARY KEY COMMENT '简历ID',
    job_description             MEDIUMTEXT      DEFAULT NULL COMMENT '目标岗位说明',
    overall_score               INT             NOT NULL DEFAULT 0 COMMENT '岗位匹配分',
    match_level                 VARCHAR(64)     NOT NULL COMMENT '匹配等级',
    summary                     TEXT            DEFAULT NULL COMMENT '匹配总结',
    matched_skills_json         TEXT            DEFAULT NULL COMMENT '命中技能JSON',
    missing_skills_json         TEXT            DEFAULT NULL COMMENT '缺失技能JSON',
    interview_focus_json        TEXT            DEFAULT NULL COMMENT '面试关注点JSON',
    risks_json                  TEXT            DEFAULT NULL COMMENT '投递风险JSON',
    learning_suggestions_json   TEXT            DEFAULT NULL COMMENT '学习建议JSON',
    create_time                 DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                 DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_overall_score (overall_score),
    INDEX idx_match_level (match_level),
    INDEX idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位匹配结果表';

-- AI 模型调用审计表
CREATE TABLE IF NOT EXISTS ai_model_call_log (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    trace_id        VARCHAR(64)     DEFAULT NULL COMMENT '请求追踪ID',
    operation_name  VARCHAR(128)    NOT NULL COMMENT '模型调用场景',
    prompt_version  VARCHAR(128)    NOT NULL COMMENT 'Prompt版本',
    model_name      VARCHAR(128)    DEFAULT NULL COMMENT '模型名称',
    success         TINYINT         NOT NULL DEFAULT 0 COMMENT '是否成功 0-失败 1-成功',
    fallback_used   TINYINT         NOT NULL DEFAULT 0 COMMENT '是否使用降级结果 0-否 1-是',
    attempt_count   INT             NOT NULL DEFAULT 1 COMMENT '实际尝试次数',
    latency_ms      BIGINT          NOT NULL DEFAULT 0 COMMENT '总耗时毫秒',
    input_tokens    INT             DEFAULT NULL COMMENT '输入token数',
    output_tokens   INT             DEFAULT NULL COMMENT '输出token数',
    total_tokens    INT             DEFAULT NULL COMMENT '总token数',
    audio_file_size_bytes BIGINT    DEFAULT NULL COMMENT '语音文件大小字节数',
    audio_sample_rate     INT       DEFAULT NULL COMMENT '语音采样率',
    audio_duration_ms     BIGINT    DEFAULT NULL COMMENT '语音时长毫秒数',
    input_token_budget      INT     DEFAULT NULL COMMENT '输入token目标预算',
    input_token_over_budget INT     DEFAULT NULL COMMENT '输入token超预算数量',
    budget_exceeded         TINYINT DEFAULT NULL COMMENT '输入token是否超预算 0-否 1-是',
    budget_uncovered        TINYINT DEFAULT NULL COMMENT '超预算但未裁剪 0-否 1-是',
    prompt_chars    INT             DEFAULT NULL COMMENT '最终Prompt字符数',
    context_clipped TINYINT         DEFAULT NULL COMMENT '上下文是否被裁剪 0-否 1-是',
    clipped_chars   INT             DEFAULT NULL COMMENT '被裁剪字符数',
    error_message   VARCHAR(1024)   DEFAULT NULL COMMENT '错误信息',
    error_type      VARCHAR(64)     DEFAULT NULL COMMENT '错误类型',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_trace_id (trace_id),
    INDEX idx_operation_create_time (operation_name, create_time),
    INDEX idx_prompt_version_create_time (prompt_version, create_time),
    INDEX idx_success_create_time (success, create_time),
    INDEX idx_model_name_create_time (model_name, create_time),
    INDEX idx_error_type_create_time (error_type, create_time)
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
