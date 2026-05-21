package com.xkh.ai.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 简历评分结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeScoreResultDTO {
    
    /**
     * 总分
     */
    @NotNull
    @Min(0)
    @Max(100)
    private Integer overallScore;
    
    /**
     * 评分详情
     */
    @Valid
    @NotNull
    private ScoreDetail scoreDetail;
    
    /**
     * 总结
     */
    @NotNull
    private String summary;
    
    /**
     * 优势列表
     */
    @NotNull
    private List<@NotNull String> strengths;
    
    /**
     * 改进建议列表
     */
    @Valid
    @NotNull
    private List<@Valid Suggestion> suggestions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreDetail {
        @NotNull
        private Integer projectScore;
        @NotNull
        private Integer skillMatchScore;
        @NotNull
        private Integer contentScore;
        @NotNull
        private Integer structureScore;
        @NotNull
        private Integer expressionScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Suggestion {
        @NotNull
        private String category;
        @NotNull
        @Pattern(regexp = "高|中|低")
        private String priority;
        @NotNull
        private String issue;
        @NotNull
        private String recommendation;
    }
}

