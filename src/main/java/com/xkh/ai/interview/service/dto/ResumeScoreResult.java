package com.xkh.ai.interview.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ResumeScoreResult {
    
    /**
     * 总分
     */
    private Integer overallScore;
    
    /**
     * 评分详情
     */
    private ScoreDetail scoreDetail;
    
    /**
     * 总结
     */
    private String summary;
    
    /**
     * 优势列表
     */
    private List<String> strengths;
    
    /**
     * 改进建议列表
     */
    private List<Suggestion> suggestions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreDetail {
        private Integer projectScore;
        private Integer skillMatchScore;
        private Integer contentScore;
        private Integer structureScore;
        private Integer expressionScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Suggestion {
        private String category;
        private String priority;
        private String issue;
        private String recommendation;
    }
}
