USE ai_interview;

-- 简历评分拆分表：把总分和五个评分维度从 resume_info JSON 中拆出来，便于统计和排序。
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

-- 面试问题明细表：按题目维度保存，便于统计问题类型和后续单题评估。
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

-- 面试评估拆分表：把总分、题量、整体反馈拆出来，复杂列表暂保留 JSON。
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

-- 岗位匹配结果表：让岗位匹配结果落库，不再只依赖浏览器 localStorage。
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
