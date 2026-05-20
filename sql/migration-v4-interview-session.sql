USE ai_interview;

-- 面试流程状态表：记录每份简历当前走到哪个业务阶段，以及最近失败阶段和原因。
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
