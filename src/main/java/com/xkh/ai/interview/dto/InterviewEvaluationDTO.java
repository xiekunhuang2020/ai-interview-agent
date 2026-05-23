package com.xkh.ai.interview.dto;

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
    @NotBlank
    @Size(max = 4000)
    private String overallFeedback;
    
    /**
     * 优势列表
     */
    @NotNull
    @Size(max = 20)
    private List<@NotBlank String> strengths;
    
    /**
     * 改进建议列表
     */
    @NotNull
    @Size(max = 20)
    private List<@NotBlank String> improvements;
    
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
        @NotNull
        @Size(max = 20)
        private List<@NotBlank String> keyPoints;
    }
}

