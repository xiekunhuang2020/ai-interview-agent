package com.xkh.ai.interview.service.tool;

import com.xkh.ai.interview.dto.InterviewEvaluationDTO;
import com.xkh.ai.interview.dto.ResumeDataDTO;
import com.xkh.ai.interview.dto.ResumeScoreResultDTO;
import com.xkh.ai.interview.service.llm.PromptContextBudgetService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResumeAgentTools {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;
    private static final int MAX_PROFILE_LIST_SIZE = 5;
    private static final int MAX_QUESTION_COUNT = 10;

    private final ResumeRepositoryTool resumeRepositoryTool;
    private final ResumeVectorTool resumeVectorTool;
    private final PromptContextBudgetService contextBudgetService;

    /**
     * 注入简历存储和向量检索工具，为 AI 顾问暴露只读查询能力。
     */
    public ResumeAgentTools(ResumeRepositoryTool resumeRepositoryTool,
                            ResumeVectorTool resumeVectorTool,
                            PromptContextBudgetService contextBudgetService) {
        this.resumeRepositoryTool = resumeRepositoryTool;
        this.resumeVectorTool = resumeVectorTool;
        this.contextBudgetService = contextBudgetService;
    }

    /**
     * 查询候选人的简历画像摘要，避免把完整简历原文暴露给模型工具调用。
     */
    @Tool(name = "get_resume_profile", description = "只读工具。根据 resumeId 查询候选人的简历画像、评分摘要、优势、建议以及面试流程状态；返回内容会裁剪，不返回完整简历原文。")
    public ResumeProfileToolResult getResumeProfile(
            @ToolParam(required = true, description = "简历唯一标识 resumeId。") String resumeId) {
        ResumeDataDTO resumeData = requireResume(resumeId);
        ResumeScoreResultDTO scoreResult = resumeData.getScoreResult();
        InterviewEvaluationDTO evaluation = resumeData.getEvaluation();

        return new ResumeProfileToolResult(
                resumeData.getResumeId(),
                scoreResult == null ? null : scoreResult.getOverallScore(),
                scoreResult == null ? "" : scoreResult.getSummary(),
                scoreResult == null ? List.of() : nullToEmpty(scoreResult.getStrengths()),
                scoreResult == null ? List.of() : summarizeSuggestions(scoreResult.getSuggestions()),
                resumeData.getQuestions() != null && resumeData.getQuestions().getQuestions() != null
                        && !resumeData.getQuestions().getQuestions().isEmpty(),
                evaluation == null ? null : evaluation.getOverallScore(),
                evaluation == null ? "" : evaluation.getOverallFeedback(),
                contextBudgetService.limitToolResumeSnippet(resumeData.getResumeText())
        );
    }

    /**
     * 查询候选人已生成的面试题摘要，最多返回前 10 题。
     */
    @Tool(name = "get_resume_interview_questions", description = "只读工具。根据 resumeId 查询候选人已生成的面试问题；最多返回前 10 题，每题内容会裁剪。")
    public InterviewQuestionsToolResult getResumeInterviewQuestions(
            @ToolParam(required = true, description = "简历唯一标识 resumeId。") String resumeId) {
        ResumeDataDTO resumeData = requireResume(resumeId);
        if (resumeData.getQuestions() == null || resumeData.getQuestions().getQuestions() == null) {
            return new InterviewQuestionsToolResult(resumeId, 0, List.of());
        }
        List<QuestionToolItem> questions = resumeData.getQuestions().getQuestions().stream()
                .limit(MAX_QUESTION_COUNT)
                .map(question -> new QuestionToolItem(
                        contextBudgetService.limitToolQuestion(question.getQuestion()),
                        nullToEmpty(question.getType()),
                        nullToEmpty(question.getCategory())
                ))
                .toList();

        return new InterviewQuestionsToolResult(
                resumeId,
                resumeData.getQuestions().getQuestions().size(),
                questions
        );
    }

    /**
     * 根据岗位 JD 或技能关键词检索相似简历片段，返回带来源类型的裁剪结果。
     */
    @Tool(name = "search_similar_resumes", description = "只读工具。根据岗位 JD、技能关键词或面试关注点检索相似简历片段；返回 sourceType=CURRENT_RESUME_FACT 时才代表当前候选人，sourceType=SIMILAR_RESUME_REFERENCE 时只能作为参考。")
    public List<SimilarResumeToolResult> searchSimilarResumes(
            @ToolParam(required = true, description = "检索文本，推荐传入岗位 JD 或核心技能要求。") String queryText,
            @ToolParam(required = false, description = "返回数量，范围 1-10，默认 5。") Integer topK,
            @ToolParam(required = false, description = "当前候选人的 resumeId，用于标记检索结果是否来自当前候选人。") String currentResumeId) {
        if (StringUtils.isBlank(queryText)) {
            throw new IllegalArgumentException("queryText 不能为空");
        }

        return resumeVectorTool.search(queryText, normalizeTopK(topK)).stream()
                .map(document -> toSearchResult(document, currentResumeId))
                .toList();
    }

    /**
     * 校验 resumeId 并查询简历数据，找不到时抛出明确的业务异常。
     */
    private ResumeDataDTO requireResume(String resumeId) {
        if (StringUtils.isBlank(resumeId)) {
            throw new IllegalArgumentException("resumeId 不能为空");
        }
        ResumeDataDTO resumeData = resumeRepositoryTool.findById(resumeId);
        if (resumeData == null) {
            throw new IllegalArgumentException("简历不存在：" + resumeId);
        }
        return resumeData;
    }

    /**
     * 将向量检索返回的 Document 转为模型工具可读的相似简历片段。
     */
    private SimilarResumeToolResult toSearchResult(Document document, String currentResumeId) {
        Object resumeId = document.getMetadata().get("resumeId");
        Object fileName = document.getMetadata().get("fileName");
        String sourceResumeId = resumeId == null ? "" : resumeId.toString();
        String sourceType = StringUtils.equals(sourceResumeId, currentResumeId)
                ? "CURRENT_RESUME_FACT"
                : "SIMILAR_RESUME_REFERENCE";
        return new SimilarResumeToolResult(
                sourceType,
                sourceName(sourceType),
                sourceResumeId,
                fileName == null ? "" : fileName.toString(),
                metadataInteger(document, "chunkIndex"),
                metadataInteger(document, "chunkCount"),
                contextBudgetService.limitToolSearchSnippet(document.getText())
        );
    }

    /**
     * 将来源枚举转成中文名称，方便模型和审计记录直接阅读。
     */
    private String sourceName(String sourceType) {
        if ("CURRENT_RESUME_FACT".equals(sourceType)) {
            return "当前简历事实";
        }
        return "相似简历参考";
    }

    /**
     * 规整工具调用传入的 topK，避免模型请求过大的检索数量。
     */
    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.max(1, Math.min(topK, MAX_TOP_K));
    }

    /**
     * 将结构化优化建议压缩为短文本列表，减少工具返回内容长度。
     */
    private List<String> summarizeSuggestions(List<ResumeScoreResultDTO.Suggestion> suggestions) {
        if (suggestions == null) {
            return List.of();
        }
        return suggestions.stream()
                .limit(MAX_PROFILE_LIST_SIZE)
                .map(suggestion -> "%s/%s：%s -> %s".formatted(
                        nullToEmpty(suggestion.getCategory()),
                        nullToEmpty(suggestion.getPriority()),
                        nullToEmpty(suggestion.getIssue()),
                        nullToEmpty(suggestion.getRecommendation())
                ))
                .toList();
    }

    /**
     * 将空列表转为空集合，并限制最多返回的条数。
     */
    private List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values.stream().limit(MAX_PROFILE_LIST_SIZE).toList();
    }

    /**
     * 将空字符串统一转为空串，避免工具结果出现 null。
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 从 Document 元数据中读取整型值，用于返回 chunkIndex 和 chunkCount。
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

    public record ResumeProfileToolResult(
            String resumeId,
            Integer overallScore,
            String summary,
            List<String> strengths,
            List<String> suggestions,
            boolean hasInterviewQuestions,
            Integer evaluationScore,
            String evaluationFeedback,
            String resumeTextSnippet
    ) {
    }

    public record InterviewQuestionsToolResult(
            String resumeId,
            int totalQuestionCount,
            List<QuestionToolItem> questions
    ) {
    }

    public record QuestionToolItem(
            String question,
            String type,
            String category
    ) {
    }

    public record SimilarResumeToolResult(
            String sourceType,
            String sourceName,
            String resumeId,
            String fileName,
            Integer chunkIndex,
            Integer chunkCount,
            String snippet
    ) {
    }
}

