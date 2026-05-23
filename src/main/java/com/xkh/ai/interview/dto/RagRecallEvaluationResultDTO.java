package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 召回评估结果，包含整体命中率、平均耗时和每条样例明细。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRecallEvaluationResultDTO {
    private Integer topK;
    private Integer totalCases;
    private Integer hitCases;
    private Double hitRate;
    private Double avgLatencyMs;
    private List<CaseResultDTO> cases;
    private List<CaseResultDTO> missedCases;

    /**
     * 单条评估样例的召回结果。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseResultDTO {
        private String caseId;
        private String query;
        private List<String> expectedKeywords;
        private List<String> matchedKeywords;
        private Boolean hit;
        private Long latencyMs;
        private List<RetrievedDocumentDTO> retrievedDocuments;
    }

    /**
     * 被召回的向量片段摘要。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievedDocumentDTO {
        private String resumeId;
        private String fileName;
        private Integer chunkIndex;
        private Integer chunkCount;
        private Double score;
        private String snippet;
    }
}
