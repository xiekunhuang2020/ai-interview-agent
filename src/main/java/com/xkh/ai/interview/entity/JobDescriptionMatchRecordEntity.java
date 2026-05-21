package com.xkh.ai.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("jd_match_result")
public class JobDescriptionMatchRecordEntity {

    @TableId(type = IdType.INPUT)
    private String resumeId;

    private String jobDescription;

    private Integer overallScore;

    private String matchLevel;

    private String summary;

    private String matchedSkillsJson;

    private String missingSkillsJson;

    private String interviewFocusJson;

    private String risksJson;

    private String learningSuggestionsJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

