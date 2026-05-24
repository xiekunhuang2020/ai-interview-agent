package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Prompt/RAG 评测报告，聚合结构化输出、事实一致性和上下文相关性结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptRagEvaluationResultDTO {
    private String generatedAt;
    private Integer totalCases;
    private Integer totalChecks;
    private Integer passedChecks;
    private Integer failedCheckCount;
    private Double passRate;
    private Double structuredOutputSuccessRate;
    private Double contextRelevancePassRate;
    private Double factConsistencyPassRate;
    private Double avgLatencyMs;
    private List<CheckResultDTO> checks;

    /**
     * 单个评测检查点，记录场景、评估器、分数、状态和页面说明。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckResultDTO {
        private String caseId;
        private String caseName;
        private String scenario;
        private String dimension;
        private String evaluatorName;
        private Boolean passed;
        private Float score;
        private Long latencyMs;
        private String message;
    }
}
