package com.xkh.ai.interview.service.rag;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.dto.InterviewQuestionsDTO;
import com.xkh.ai.interview.dto.JobDescriptionMatchResultDTO;
import com.xkh.ai.interview.dto.PromptRagEvaluationCaseDTO;
import com.xkh.ai.interview.dto.PromptRagEvaluationResultDTO;
import com.xkh.ai.interview.dto.ResumeScoreResultDTO;
import com.xkh.ai.interview.service.agent.JobDescriptionMatchAgent;
import com.xkh.ai.interview.service.agent.RagInterviewQuestionAgent;
import com.xkh.ai.interview.service.agent.ResumeAnalysisAgent;
import com.xkh.ai.interview.service.tool.ResumeVectorTool;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Prompt/RAG 评测回放服务：业务侧只编排样例，评估交给 Spring AI 官方 Evaluator。
 */
@Service
public class PromptRagEvaluationService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;
    private static final String DIMENSION_STRUCTURE = "结构化输出";
    private static final String DIMENSION_RELEVANCE = "上下文相关性";
    private static final String DIMENSION_FACT = "事实一致性";

    private final ResumeAnalysisAgent resumeAnalysisAgent;
    private final JobDescriptionMatchAgent jobDescriptionMatchAgent;
    private final RagInterviewQuestionAgent ragInterviewQuestionAgent;
    private final ResumeVectorTool resumeVectorTool;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final RelevancyEvaluator relevancyEvaluator;
    private final FactCheckingEvaluator factCheckingEvaluator;
    private final String evaluationFileLocation;

    /**
     * 注入业务 Agent、向量工具和 Spring AI 官方评估器。
     */
    public PromptRagEvaluationService(ResumeAnalysisAgent resumeAnalysisAgent,
                                      JobDescriptionMatchAgent jobDescriptionMatchAgent,
                                      RagInterviewQuestionAgent ragInterviewQuestionAgent,
                                      ResumeVectorTool resumeVectorTool,
                                      ObjectMapper objectMapper,
                                      ResourceLoader resourceLoader,
                                      ChatClient.Builder chatClientBuilder,
                                      @Value("${ai-interview.evaluation.prompt-rag-file:file:samples/eval/prompt-rag-evaluation-cases.json}") String evaluationFileLocation) {
        this.resumeAnalysisAgent = resumeAnalysisAgent;
        this.jobDescriptionMatchAgent = jobDescriptionMatchAgent;
        this.ragInterviewQuestionAgent = ragInterviewQuestionAgent;
        this.resumeVectorTool = resumeVectorTool;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.relevancyEvaluator = new RelevancyEvaluator(chatClientBuilder);
        this.factCheckingEvaluator = FactCheckingEvaluator.builder(chatClientBuilder).build();
        this.evaluationFileLocation = evaluationFileLocation;
    }

    /**
     * 运行固定样例集，返回运营看板展示用的评测报告。
     */
    public PromptRagEvaluationResultDTO evaluate(Integer topK) {
        int defaultTopK = normalizeTopK(topK);
        List<PromptRagEvaluationCaseDTO> cases = loadEvaluationCases();
        List<PromptRagEvaluationResultDTO.CheckResultDTO> checks = cases.stream()
                .flatMap(evaluationCase -> evaluateCase(evaluationCase, defaultTopK).stream())
                .toList();
        long passed = checks.stream()
                .filter(check -> Boolean.TRUE.equals(check.getPassed()))
                .count();

        return PromptRagEvaluationResultDTO.builder()
                .generatedAt(LocalDateTime.now().toString())
                .totalCases(cases.size())
                .totalChecks(checks.size())
                .passedChecks((int) passed)
                .failedCheckCount(checks.size() - (int) passed)
                .passRate(rate(passed, checks.size()))
                .structuredOutputSuccessRate(dimensionRate(checks, DIMENSION_STRUCTURE))
                .contextRelevancePassRate(dimensionRate(checks, DIMENSION_RELEVANCE))
                .factConsistencyPassRate(dimensionRate(checks, DIMENSION_FACT))
                .avgLatencyMs(round(checks.stream()
                        .mapToLong(check -> check.getLatencyMs() == null ? 0L : check.getLatencyMs())
                        .average()
                        .orElse(0D)))
                .checks(checks)
                .build();
    }

    /**
     * 回放单个样例，依次覆盖简历分析、岗位匹配和 RAG 出题。
     */
    private List<PromptRagEvaluationResultDTO.CheckResultDTO> evaluateCase(PromptRagEvaluationCaseDTO evaluationCase, int defaultTopK) {
        String resumeText = readText(evaluationCase.getResumeFile());
        String jobDescription = readText(evaluationCase.getJobDescriptionFile());
        List<PromptRagEvaluationResultDTO.CheckResultDTO> checks = new ArrayList<>();
        checks.addAll(evaluateResumeAnalysis(evaluationCase, resumeText));
        checks.addAll(evaluateJobMatch(evaluationCase, resumeText, jobDescription));
        checks.addAll(evaluateRagQuestions(evaluationCase, resumeText, jobDescription, resolveTopK(evaluationCase, defaultTopK)));
        return checks;
    }

    /**
     * 调用简历分析 Agent，并检查结构化输出和事实一致性。
     */
    private List<PromptRagEvaluationResultDTO.CheckResultDTO> evaluateResumeAnalysis(PromptRagEvaluationCaseDTO evaluationCase,
                                                                                     String resumeText) {
        long start = System.currentTimeMillis();
        try {
            ResumeScoreResultDTO result = resumeAnalysisAgent.analyze(resumeText);
            return List.of(
                    dtoCheck(evaluationCase, "简历分析", start, "总分：" + result.getOverallScore()),
                    factCheck(evaluationCase, "简历分析", List.of(new Document(resumeText)), toJson(result))
            );
        } catch (Exception e) {
            return List.of(failedDtoCheck(evaluationCase, "简历分析", start, e));
        }
    }

    /**
     * 调用岗位匹配 Agent，并检查结构化输出、相关性和事实一致性。
     */
    private List<PromptRagEvaluationResultDTO.CheckResultDTO> evaluateJobMatch(PromptRagEvaluationCaseDTO evaluationCase,
                                                                               String resumeText,
                                                                               String jobDescription) {
        long start = System.currentTimeMillis();
        try {
            JobDescriptionMatchResultDTO result = jobDescriptionMatchAgent.match(resumeText, jobDescription);
            List<Document> context = context(resumeText, jobDescription);
            String output = toJson(result);
            return List.of(
                    dtoCheck(evaluationCase, "岗位匹配", start, "匹配分：" + result.getOverallScore()),
                    relevanceCheck(evaluationCase, "岗位匹配", "判断候选人和目标岗位是否匹配。", context, output),
                    factCheck(evaluationCase, "岗位匹配", context, output)
            );
        } catch (Exception e) {
            return List.of(failedDtoCheck(evaluationCase, "岗位匹配", start, e));
        }
    }

    /**
     * 调用 RAG 出题 Agent，并检查结构化输出、题量、相关性和事实一致性。
     */
    private List<PromptRagEvaluationResultDTO.CheckResultDTO> evaluateRagQuestions(PromptRagEvaluationCaseDTO evaluationCase,
                                                                                   String resumeText,
                                                                                   String jobDescription,
                                                                                   int topK) {
        long start = System.currentTimeMillis();
        try {
            List<Document> context = context(resumeText, jobDescription);
            context.addAll(indexReferenceResumes(evaluationCase));
            InterviewQuestionsDTO result = ragInterviewQuestionAgent.generate(
                    resumeText, jobDescription, "eval-current-" + sanitizeId(evaluationCase.getCaseId()), topK);
            String output = toJson(result);
            return List.of(
                    dtoCheck(evaluationCase, "岗位定制出题", start, "题目数：" + questionCount(result)),
                    relevanceCheck(evaluationCase, "岗位定制出题", "基于候选人简历和目标岗位生成面试题。", context, output),
                    factCheck(evaluationCase, "岗位定制出题", context, output)
            );
        } catch (Exception e) {
            return List.of(failedDtoCheck(evaluationCase, "岗位定制出题", start, e));
        }
    }

    /**
     * 使用 Spring AI RelevancyEvaluator 判断输出是否贴合上下文。
     */
    private PromptRagEvaluationResultDTO.CheckResultDTO relevanceCheck(PromptRagEvaluationCaseDTO evaluationCase,
                                                                       String scenario,
                                                                       String query,
                                                                       List<Document> context,
                                                                       String output) {
        long start = System.currentTimeMillis();
        try {
            EvaluationResponse response = relevancyEvaluator.evaluate(new EvaluationRequest(query, context, output));
            return evaluatorCheck(evaluationCase, scenario, DIMENSION_RELEVANCE,
                    "Spring AI RelevancyEvaluator", response, start);
        } catch (Exception e) {
            return check(evaluationCase, scenario, DIMENSION_RELEVANCE,
                    "Spring AI RelevancyEvaluator", false, 0F, elapsed(start), e.getMessage());
        }
    }

    /**
     * 使用 Spring AI FactCheckingEvaluator 判断输出事实是否有上下文支撑。
     */
    private PromptRagEvaluationResultDTO.CheckResultDTO factCheck(PromptRagEvaluationCaseDTO evaluationCase,
                                                                  String scenario,
                                                                  List<Document> context,
                                                                  String output) {
        long start = System.currentTimeMillis();
        try {
            EvaluationResponse response = factCheckingEvaluator.evaluate(new EvaluationRequest(context, output));
            return evaluatorCheck(evaluationCase, scenario, DIMENSION_FACT,
                    "Spring AI FactCheckingEvaluator", response, start);
        } catch (Exception e) {
            return check(evaluationCase, scenario, DIMENSION_FACT,
                    "Spring AI FactCheckingEvaluator", false, 0F, elapsed(start), e.getMessage());
        }
    }

    /**
     * 构建结构化输出成功检查项。
     */
    private PromptRagEvaluationResultDTO.CheckResultDTO dtoCheck(PromptRagEvaluationCaseDTO evaluationCase,
                                                                 String scenario,
                                                                 long start,
                                                                 String message) {
        return check(evaluationCase, scenario, DIMENSION_STRUCTURE,
                "DTO 校验", true, 1F, elapsed(start), message);
    }

    /**
     * 构建结构化输出失败检查项。
     */
    private PromptRagEvaluationResultDTO.CheckResultDTO failedDtoCheck(PromptRagEvaluationCaseDTO evaluationCase,
                                                                       String scenario,
                                                                       long start,
                                                                       Exception e) {
        return check(evaluationCase, scenario, DIMENSION_STRUCTURE,
                "DTO 校验", false, 0F, elapsed(start), e.getMessage());
    }

    /**
     * 将官方 Evaluator 响应转换为页面检查项。
     */
    private PromptRagEvaluationResultDTO.CheckResultDTO evaluatorCheck(PromptRagEvaluationCaseDTO evaluationCase,
                                                                       String scenario,
                                                                       String dimension,
                                                                       String evaluatorName,
                                                                       EvaluationResponse response,
                                                                       long start) {
        String message = StringUtils.defaultIfBlank(response.getFeedback(),
                response.isPass() ? "官方评估通过。" : "官方评估未通过。");
        return check(evaluationCase, scenario, dimension, evaluatorName,
                response.isPass(), score(response), elapsed(start), message);
    }

    /**
     * 构建页面展示用的统一检查项。
     */
    private PromptRagEvaluationResultDTO.CheckResultDTO check(PromptRagEvaluationCaseDTO evaluationCase,
                                                              String scenario,
                                                              String dimension,
                                                              String evaluatorName,
                                                              boolean passed,
                                                              Float score,
                                                              long latencyMs,
                                                              String message) {
        return PromptRagEvaluationResultDTO.CheckResultDTO.builder()
                .caseId(evaluationCase.getCaseId())
                .caseName(evaluationCase.getName())
                .scenario(scenario)
                .dimension(dimension)
                .evaluatorName(evaluatorName)
                .passed(passed)
                .score(score)
                .latencyMs(latencyMs)
                .message(StringUtils.defaultString(message))
                .build();
    }

    /**
     * 将参考简历写入向量库，并作为官方 Evaluator 的上下文。
     */
    private List<Document> indexReferenceResumes(PromptRagEvaluationCaseDTO evaluationCase) {
        List<Document> documents = new ArrayList<>();
        List<String> files = nullToEmpty(evaluationCase.getReferenceResumeFiles());
        for (int i = 0; i < files.size(); i++) {
            String file = files.get(i);
            String text = readText(file);
            resumeVectorTool.replaceResume("eval-reference-" + sanitizeId(evaluationCase.getCaseId()) + "-" + i,
                    file, text);
            documents.add(new Document(text));
        }
        return documents;
    }

    /**
     * 创建简历和岗位 JD 组成的官方 Evaluator 上下文。
     */
    private List<Document> context(String resumeText, String jobDescription) {
        List<Document> documents = new ArrayList<>();
        documents.add(new Document(resumeText));
        documents.add(new Document(jobDescription));
        return documents;
    }

    /**
     * 读取评测样例配置文件。
     */
    private List<PromptRagEvaluationCaseDTO> loadEvaluationCases() {
        Resource resource = resource(evaluationFileLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("Prompt/RAG 评测集不存在：" + evaluationFileLocation);
        }
        try {
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, PromptRagEvaluationCaseDTO.class);
            List<PromptRagEvaluationCaseDTO> cases = objectMapper.readValue(resource.getInputStream(), listType);
            if (cases == null || cases.isEmpty()) {
                throw new IllegalStateException("Prompt/RAG 评测集为空：" + evaluationFileLocation);
            }
            return cases;
        } catch (IOException e) {
            throw new IllegalStateException("读取 Prompt/RAG 评测集失败：" + evaluationFileLocation, e);
        }
    }

    /**
     * 读取样例文本文件，支持 file:、classpath: 或项目根目录相对路径。
     */
    private String readText(String location) {
        if (StringUtils.isBlank(location)) {
            throw new IllegalArgumentException("评测样例文件路径不能为空");
        }
        Resource resource = resource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("评测样例文件不存在：" + location);
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取评测样例文件失败：" + location, e);
        }
    }

    /**
     * 将路径转换为 Spring Resource，未声明前缀时按项目根目录文件处理。
     */
    private Resource resource(String location) {
        if (location.startsWith("file:") || location.startsWith("classpath:")) {
            return resourceLoader.getResource(location);
        }
        return resourceLoader.getResource("file:" + location);
    }

    /**
     * 将业务结果转成 JSON，交给官方 Evaluator 判断。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 统计面试题数量，避免空结果导致评测中断。
     */
    private int questionCount(InterviewQuestionsDTO result) {
        return result == null || result.getQuestions() == null ? 0 : result.getQuestions().size();
    }

    /**
     * 计算某个维度的通过率。
     */
    private double dimensionRate(List<PromptRagEvaluationResultDTO.CheckResultDTO> checks, String dimension) {
        List<PromptRagEvaluationResultDTO.CheckResultDTO> filteredChecks = checks.stream()
                .filter(check -> dimension.equals(check.getDimension()))
                .toList();
        long passed = filteredChecks.stream()
                .filter(check -> Boolean.TRUE.equals(check.getPassed()))
                .count();
        return rate(passed, filteredChecks.size());
    }

    /**
     * 计算百分比并保留两位小数。
     */
    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0D : round(numerator * 100D / denominator);
    }

    /**
     * 计算从开始时间到当前的耗时。
     */
    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    /**
     * 归一化评测使用的 RAG TopK。
     */
    private int normalizeTopK(Integer topK) {
        return topK == null ? DEFAULT_TOP_K : Math.max(1, Math.min(topK, MAX_TOP_K));
    }

    /**
     * 读取样例自己的 TopK；未配置时使用页面传入值。
     */
    private int resolveTopK(PromptRagEvaluationCaseDTO evaluationCase, int defaultTopK) {
        return evaluationCase.getTopK() == null ? defaultTopK : normalizeTopK(evaluationCase.getTopK());
    }

    /**
     * 将空列表统一转为空集合。
     */
    private List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 将 Spring AI Evaluator 的分数补齐到 0 或 1。
     */
    private float score(EvaluationResponse response) {
        return response.getScore() > 0F ? response.getScore() : (response.isPass() ? 1F : 0F);
    }

    /**
     * 清理样例 ID，确保可作为评测向量 resumeId 的一部分。
     */
    private String sanitizeId(String value) {
        return StringUtils.defaultIfBlank(value, "unknown")
                .replaceAll("[^A-Za-z0-9_-]", "-");
    }

    /**
     * 将浮点数四舍五入到两位小数。
     */
    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
