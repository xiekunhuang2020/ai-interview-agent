package com.xkh.ai.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @NotBlank
    @Size(max = 2000)
    private String summary;
    
    /**
     * 优势列表
     */
    @NotNull
    @Size(max = 20)
    private List<@NotBlank String> strengths;
    
    /**
     * 改进建议列表
     */
    @Valid
    @NotNull
    @Size(max = 20)
    private List<@Valid Suggestion> suggestions;

    @Data
    @NoArgsConstructor
    public static class ScoreDetail {
        @NotNull
        @Min(0)
        @Max(40)
        private Integer projectScore;
        @NotNull
        @Min(0)
        @Max(20)
        private Integer skillMatchScore;
        @NotNull
        @Min(0)
        @Max(15)
        private Integer contentScore;
        @NotNull
        @Min(0)
        @Max(15)
        private Integer structureScore;
        @NotNull
        @Min(0)
        @Max(10)
        private Integer expressionScore;

        /**
         * 创建评分明细，并把模型偶发越界的分数裁剪到业务允许范围内。
         */
        public ScoreDetail(Integer projectScore,
                           Integer skillMatchScore,
                           Integer contentScore,
                           Integer structureScore,
                           Integer expressionScore) {
            setProjectScore(projectScore);
            setSkillMatchScore(skillMatchScore);
            setContentScore(contentScore);
            setStructureScore(structureScore);
            setExpressionScore(expressionScore);
        }

        /**
         * 设置项目深度分，范围固定为 0-40。
         */
        public void setProjectScore(Integer projectScore) {
            this.projectScore = clampScore(projectScore, 0, 40);
        }

        /**
         * 设置技能匹配分，范围固定为 0-20。
         */
        public void setSkillMatchScore(Integer skillMatchScore) {
            this.skillMatchScore = clampScore(skillMatchScore, 0, 20);
        }

        /**
         * 设置内容完整分，范围固定为 0-15。
         */
        public void setContentScore(Integer contentScore) {
            this.contentScore = clampScore(contentScore, 0, 15);
        }

        /**
         * 设置结构清晰分，范围固定为 0-15。
         */
        public void setStructureScore(Integer structureScore) {
            this.structureScore = clampScore(structureScore, 0, 15);
        }

        /**
         * 设置表达质量分，范围固定为 0-10。
         */
        public void setExpressionScore(Integer expressionScore) {
            this.expressionScore = clampScore(expressionScore, 0, 10);
        }

        /**
         * 将模型偶发越界的分数裁剪到指定区间，空值交给 Bean Validation 报错。
         */
        private Integer clampScore(Integer value, int min, int max) {
            if (value == null) {
                return null;
            }
            return Math.max(min, Math.min(max, value));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Suggestion {
        @NotBlank
        @Size(max = 64)
        private String category;
        @NotBlank
        @Pattern(regexp = "高|中|低")
        private String priority;
        @NotBlank
        @Size(max = 1000)
        private String issue;
        @NotBlank
        @Size(max = 2000)
        private String recommendation;
    }
}

