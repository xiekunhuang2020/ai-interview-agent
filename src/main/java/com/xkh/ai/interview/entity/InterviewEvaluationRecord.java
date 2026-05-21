package com.xkh.ai.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_evaluation")
public class InterviewEvaluationRecord {

    @TableId(type = IdType.INPUT)
    private String resumeId;

    private String sessionId;

    private Integer totalQuestions;

    private Integer overallScore;

    private String overallFeedback;

    private String categoryScoresJson;

    private String questionDetailsJson;

    private String strengthsJson;

    private String improvementsJson;

    private String referenceAnswersJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
