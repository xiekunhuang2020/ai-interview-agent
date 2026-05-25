package com.xkh.ai.interview.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 面试评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewEvaluationDTO {
    
    /**
     * 面试会话 ID
     */
    @NotBlank
    @Size(max = 128)
    private String sessionId;
    
    /**
     * 总问题数
     */
    @NotNull
    @Min(0)
    @Max(200)
    private Integer totalQuestions;
    
    /**
     * 总分
     */
    @NotNull
    @Min(0)
    @Max(100)
    private Integer overallScore;
    
    /**
     * 分类得分统计
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @Valid
    @NotNull
    private List<@Valid CategoryScore> categoryScores;
    
    /**
     * 问题详情
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @Valid
    @NotNull
    private List<@Valid QuestionDetail> questionDetails;
    
    /**
     * 整体反馈
     */
    @NotBlank
    @Size(max = 4000)
    private String overallFeedback;
    
    /**
     * 优势列表
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @NotNull
    @Size(max = 20)
    private List<@NotBlank String> strengths;
    
    /**
     * 改进建议列表
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @NotNull
    @Size(max = 20)
    private List<@NotBlank String> improvements;
    
    /**
     * 参考答案列表
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @Valid
    @NotNull
    private List<@Valid ReferenceAnswer> referenceAnswers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryScore {
        @NotBlank
        @Size(max = 128)
        private String category;
        @NotNull
        @Min(0)
        @Max(100)
        private Integer score;
        @NotNull
        @Min(0)
        @Max(200)
        private Integer questionCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDetail {
        @NotNull
        @Min(0)
        @Max(200)
        private Integer questionIndex;
        @NotBlank
        @Size(max = 1000)
        private String question;
        @NotBlank
        @Size(max = 128)
        private String category;
        @NotBlank
        @Size(max = 4000)
        private String userAnswer;
        @NotNull
        @Min(0)
        @Max(100)
        private Integer score;
        @NotBlank
        @Size(max = 4000)
        private String feedback;
        /**
         * 回答内容层面的主要问题，例如概念错误、遗漏关键点或缺少业务场景。
         */
        @Size(max = 2000)
        private String contentIssue;
        /**
         * 表达层面的主要问题，例如逻辑顺序、术语准确性或陈述是否啰嗦。
         */
        @Size(max = 2000)
        private String expressionIssue;
        /**
         * 针对本题的回答结构优化建议，用于指导候选人按背景、动作、结果重讲。
         */
        @Size(max = 2000)
        private String structureSuggestion;
        /**
         * 面试官可能基于当前回答继续追问的问题。
         */
        @Size(max = 1000)
        private String followUpQuestion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceAnswer {
        @NotNull
        @Min(0)
        @Max(200)
        private Integer questionIndex;
        @NotBlank
        @Size(max = 1000)
        private String question;
        @NotBlank
        @Size(max = 4000)
        private String referenceAnswer;
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        @NotNull
        @Size(max = 20)
        private List<@NotBlank String> keyPoints;
    }
}

