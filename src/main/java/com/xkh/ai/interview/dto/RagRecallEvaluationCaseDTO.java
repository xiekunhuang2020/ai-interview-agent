package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 召回评估的单条样例，描述查询文本和期望命中的关键词。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRecallEvaluationCaseDTO {
    private String caseId;
    private String query;
    private List<String> expectedKeywords;
    private String note;
}
