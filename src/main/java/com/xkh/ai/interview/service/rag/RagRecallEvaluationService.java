package com.xkh.ai.interview.service.rag;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.dto.RagRecallEvaluationCaseDTO;
import com.xkh.ai.interview.dto.RagRecallEvaluationResultDTO;
import com.xkh.ai.interview.service.tool.ResumeVectorTool;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RagRecallEvaluationService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;
    private static final int MAX_SNIPPET_CHARS = 260;

    private final ResumeVectorTool resumeVectorTool;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String evaluationFileLocation;

    /**
     * 注入向量检索工具和评估集文件位置，用于本地离线评估 RAG 召回效果。
     */
    public RagRecallEvaluationService(ResumeVectorTool resumeVectorTool,
                                      ObjectMapper objectMapper,
                                      ResourceLoader resourceLoader,
                                      @Value("${ai-interview.rag.evaluation-file:file:samples/eval/rag-recall-cases.json}") String evaluationFileLocation) {
        this.resumeVectorTool = resumeVectorTool;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.evaluationFileLocation = evaluationFileLocation;
    }

    /**
     * 运行 RAG 召回评估，输出 TopK 命中率、平均耗时和未命中样例。
     */
    public RagRecallEvaluationResultDTO evaluate(Integer topK) {
        int normalizedTopK = normalizeTopK(topK);
        List<RagRecallEvaluationCaseDTO> cases = loadEvaluationCases();
        List<RagRecallEvaluationResultDTO.CaseResultDTO> caseResults = new ArrayList<>();

        for (RagRecallEvaluationCaseDTO evaluationCase : cases) {
            caseResults.add(evaluateCase(evaluationCase, normalizedTopK));
        }

        long hitCases = caseResults.stream()
                .filter(result -> Boolean.TRUE.equals(result.getHit()))
                .count();
        double avgLatencyMs = caseResults.stream()
                .mapToLong(RagRecallEvaluationResultDTO.CaseResultDTO::getLatencyMs)
                .average()
                .orElse(0);
        List<RagRecallEvaluationResultDTO.CaseResultDTO> missedCases = caseResults.stream()
                .filter(result -> !Boolean.TRUE.equals(result.getHit()))
                .toList();

        return RagRecallEvaluationResultDTO.builder()
                .topK(normalizedTopK)
                .totalCases(caseResults.size())
                .hitCases((int) hitCases)
                .hitRate(caseResults.isEmpty() ? 0 : round(hitCases * 1.0 / caseResults.size()))
                .avgLatencyMs(round(avgLatencyMs))
                .cases(caseResults)
                .missedCases(missedCases)
                .build();
    }

    /**
     * 从 samples/eval 读取评估样例，保持评估数据和业务代码分离。
     */
    private List<RagRecallEvaluationCaseDTO> loadEvaluationCases() {
        Resource resource = resourceLoader.getResource(evaluationFileLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("RAG 评估集不存在：" + evaluationFileLocation);
        }

        try {
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, RagRecallEvaluationCaseDTO.class);
            List<RagRecallEvaluationCaseDTO> cases = objectMapper.readValue(resource.getInputStream(), listType);
            if (cases == null || cases.isEmpty()) {
                throw new IllegalStateException("RAG 评估集为空：" + evaluationFileLocation);
            }
            return cases;
        } catch (IOException e) {
            throw new IllegalStateException("读取 RAG 评估集失败：" + evaluationFileLocation, e);
        }
    }

    /**
     * 执行单条查询样例，并判断 TopK 检索结果是否命中预期关键词。
     */
    private RagRecallEvaluationResultDTO.CaseResultDTO evaluateCase(RagRecallEvaluationCaseDTO evaluationCase, int topK) {
        long start = System.currentTimeMillis();
        List<Document> documents = resumeVectorTool.search(evaluationCase.getQuery(), topK);
        long latencyMs = System.currentTimeMillis() - start;

        List<String> matchedKeywords = findMatchedKeywords(documents, evaluationCase.getExpectedKeywords());
        return RagRecallEvaluationResultDTO.CaseResultDTO.builder()
                .caseId(evaluationCase.getCaseId())
                .query(evaluationCase.getQuery())
                .expectedKeywords(nullToEmpty(evaluationCase.getExpectedKeywords()))
                .matchedKeywords(matchedKeywords)
                .hit(!matchedKeywords.isEmpty())
                .latencyMs(latencyMs)
                .retrievedDocuments(documents.stream()
                        .map(this::toRetrievedDocument)
                        .toList())
                .build();
    }

    /**
     * 在召回片段中查找命中的期望关键词，任一关键词命中即认为该样例召回成功。
     */
    private List<String> findMatchedKeywords(List<Document> documents, List<String> expectedKeywords) {
        List<String> keywords = nullToEmpty(expectedKeywords);
        if (documents == null || documents.isEmpty() || keywords.isEmpty()) {
            return List.of();
        }

        String joinedText = documents.stream()
                .map(Document::getText)
                .filter(StringUtils::isNotBlank)
                .reduce("", (left, right) -> left + "\n" + right)
                .toLowerCase(Locale.ROOT);
        return keywords.stream()
                .filter(StringUtils::isNotBlank)
                .filter(keyword -> joinedText.contains(keyword.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
    }

    /**
     * 将 Spring AI Document 转成评估报告中的召回片段摘要。
     */
    private RagRecallEvaluationResultDTO.RetrievedDocumentDTO toRetrievedDocument(Document document) {
        return RagRecallEvaluationResultDTO.RetrievedDocumentDTO.builder()
                .resumeId(metadataString(document, "resumeId"))
                .fileName(metadataString(document, "fileName"))
                .chunkIndex(metadataInteger(document, "chunkIndex"))
                .chunkCount(metadataInteger(document, "chunkCount"))
                .score(document.getScore())
                .snippet(truncate(document.getText()))
                .build();
    }

    /**
     * 规整 TopK 范围，防止一次评估拉取过多向量片段。
     */
    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.max(1, Math.min(topK, MAX_TOP_K));
    }

    /**
     * 从 Document 元数据中读取字符串值。
     */
    private String metadataString(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : value.toString();
    }

    /**
     * 从 Document 元数据中读取整型值。
     */
    private Integer metadataInteger(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || !StringUtils.isNumeric(value.toString())) {
            return null;
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * 将空列表统一转为空集合，避免评估过程出现空指针。
     */
    private List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 裁剪召回片段，避免评估响应过大。
     */
    private String truncate(String text) {
        if (text == null || text.length() <= MAX_SNIPPET_CHARS) {
            return text == null ? "" : text;
        }
        return text.substring(0, MAX_SNIPPET_CHARS) + "\n...[truncated]";
    }

    /**
     * 将数值保留四位小数，便于前端或 curl 直接阅读。
     */
    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
