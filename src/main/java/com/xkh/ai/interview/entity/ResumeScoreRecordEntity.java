package com.xkh.ai.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_score")
public class ResumeScoreRecordEntity {

    @TableId(type = IdType.INPUT)
    private String resumeId;

    private Integer overallScore;

    private Integer projectScore;

    private Integer skillMatchScore;

    private Integer contentScore;

    private Integer structureScore;

    private Integer expressionScore;

    private String summary;

    private String strengthsJson;

    private String suggestionsJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

