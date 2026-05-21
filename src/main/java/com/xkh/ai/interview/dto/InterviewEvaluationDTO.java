package com.xkh.ai.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    @NotNull
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
    @Valid
    @NotNull
    private List<@Valid CategoryScore> categoryScores;
    
    /**
     * 问题详情
     */
    @Valid
    @NotNull
    private List<@Valid QuestionDetail> questionDetails;
    
    /**
     * 整体反馈
     */
    @NotNull
    private String overallFeedback;
    
    /**
     * 优势列表
     */
    @NotNull
    private List<@NotNull String> strengths;
    
    /**
     * 改进建议列表
     */
    @NotNull
    private List<@NotNull String> improvements;
    
    /**
     * 参考答案列表
     */
    @Valid
    @NotNull
    private List<@Valid ReferenceAnswer> referenceAnswers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryScore {
        @NotNull
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
        @NotNull
        private String question;
        @NotNull
        private String category;
        @NotNull
        private String userAnswer;
        @NotNull
        @Min(0)
        @Max(100)
        private Integer score;
        @NotNull
        private String feedback;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceAnswer {
        @NotNull
        @Min(0)
        @Max(200)
        private Integer questionIndex;
        @NotNull
        private String question;
        @NotNull
        private String referenceAnswer;
        @NotNull
        private List<@NotNull String> keyPoints;
    }
}

