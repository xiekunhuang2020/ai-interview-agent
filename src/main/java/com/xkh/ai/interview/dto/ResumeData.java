package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeData {
    
    /**
     * 简历 ID
     */
    private String resumeId;
    
    /**
     * 简历文本内容
     */
    private String resumeText;
    
    /**
     * 评分结果
     */
    private ResumeScoreResult scoreResult;
    
    /**
     * 面试问题
     */
    private InterviewQuestions questions;
    
    /**
     * 评估结果
     */
    private InterviewEvaluation evaluation;

    /**
     * 最近一次岗位匹配结果
     */
    private JobDescriptionMatchResult matchResult;

    /**
     * 最近一次岗位匹配使用的岗位说明
     */
    private String jobDescription;
}
