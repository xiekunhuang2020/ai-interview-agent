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
