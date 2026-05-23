package com.xkh.ai.interview.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.dto.InterviewEvaluationDTO;
import com.xkh.ai.interview.dto.InterviewQuestionsDTO;
import com.xkh.ai.interview.dto.JobDescriptionMatchResultDTO;
import com.xkh.ai.interview.dto.ResumeScoreResultDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AiJsonResponseParser {

    private static final Logger logger = LoggerFactory.getLogger(AiJsonResponseParser.class);
    private static final Set<String> ALLOWED_QUESTION_TYPES = Set.of(
            "PROJECT",
            "JAVA_BASIC",
            "JAVA_COLLECTION",
            "JAVA_CONCURRENT",
            "MYSQL",
            "REDIS",
            "SPRING",
            "SPRING_BOOT",
            "AI"
    );
    private static final String CURRENT_RESUME_FACT = "CURRENT_RESUME_FACT";
    private static final String SIMILAR_RESUME_REFERENCE = "SIMILAR_RESUME_REFERENCE";
    private static final int MAX_SOURCE_NOTE_CHARS = 120;
    private static final Set<String> ALLOWED_EVIDENCE_SOURCES = Set.of(
            CURRENT_RESUME_FACT,
            SIMILAR_RESUME_REFERENCE
    );
    private static final Map<String, String> QUESTION_TYPE_ALIASES = Map.ofEntries(
            Map.entry("JAVA", "JAVA_BASIC"),
            Map.entry("JAVA_CORE", "JAVA_BASIC"),
            Map.entry("JVM", "JAVA_BASIC"),
            Map.entry("COLLECTION", "JAVA_COLLECTION"),
            Map.entry("COLLECTIONS", "JAVA_COLLECTION"),
            Map.entry("CONCURRENT", "JAVA_CONCURRENT"),
            Map.entry("THREAD", "JAVA_CONCURRENT"),
            Map.entry("THREADING", "JAVA_CONCURRENT"),
            Map.entry("DATABASE", "MYSQL"),
            Map.entry("DB", "MYSQL"),
            Map.entry("SQL", "MYSQL"),
            Map.entry("CACHE", "REDIS"),
            Map.entry("SPRINGBOOT", "SPRING_BOOT"),
            Map.entry("SPRING_BOOT", "SPRING_BOOT"),
            Map.entry("SPRING AI", "AI"),
            Map.entry("SPRING_AI", "AI"),
            Map.entry("RAG", "AI"),
            Map.entry("AI_AGENT", "AI"),
            Map.entry("AGENT", "AI"),
            Map.entry("SYSTEM_DESIGN", "PROJECT"),
            Map.entry("ARCHITECTURE", "PROJECT"),
            Map.entry("DESIGN", "PROJECT"),
            Map.entry("项目", "PROJECT"),
            Map.entry("项目经历", "PROJECT"),
            Map.entry("系统设计", "PROJECT")
    );
    private static final Map<String, String> EVIDENCE_SOURCE_ALIASES = Map.ofEntries(
            Map.entry("CURRENT", CURRENT_RESUME_FACT),
            Map.entry("RESUME", CURRENT_RESUME_FACT),
            Map.entry("CURRENT_RESUME", CURRENT_RESUME_FACT),
            Map.entry("FACT", CURRENT_RESUME_FACT),
            Map.entry("SIMILAR", SIMILAR_RESUME_REFERENCE),
            Map.entry("REFERENCE", SIMILAR_RESUME_REFERENCE),
            Map.entry("RAG", SIMILAR_RESUME_REFERENCE),
            Map.entry("SIMILAR_RESUME", SIMILAR_RESUME_REFERENCE),
            Map.entry("当前简历", CURRENT_RESUME_FACT),
            Map.entry("当前简历事实", CURRENT_RESUME_FACT),
            Map.entry("相似简历", SIMILAR_RESUME_REFERENCE),
            Map.entry("相似简历参考", SIMILAR_RESUME_REFERENCE)
    );

    private final Validator validator;
    private final BeanOutputConverter<ResumeScoreResultDTO> resumeScoreConverter;
    private final BeanOutputConverter<InterviewQuestionsDTO> interviewQuestionsConverter;
    private final BeanOutputConverter<InterviewEvaluationDTO> interviewEvaluationConverter;
    private final BeanOutputConverter<JobDescriptionMatchResultDTO> jobDescriptionMatchConverter;

    public AiJsonResponseParser(ObjectMapper objectMapper, Validator validator) {
        ObjectMapper strictObjectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, false);
        this.validator = validator;
        this.resumeScoreConverter = new BeanOutputConverter<>(ResumeScoreResultDTO.class, strictObjectMapper);
        this.interviewQuestionsConverter = new BeanOutputConverter<>(InterviewQuestionsDTO.class, strictObjectMapper);
        this.interviewEvaluationConverter = new BeanOutputConverter<>(InterviewEvaluationDTO.class, strictObjectMapper);
        this.jobDescriptionMatchConverter = new BeanOutputConverter<>(JobDescriptionMatchResultDTO.class, strictObjectMapper);
    }

    public ResumeScoreResultDTO parseResumeScoreResult(String json) throws JsonProcessingException {
        ResumeScoreResultDTO result = convertWithSpringAi(json, resumeScoreConverter, "resume-score");
        validateBean(result, "resume-score");
        normalizeResumeScoreDetail(result);
        return result;
    }

    public InterviewQuestionsDTO parseInterviewQuestions(String json) throws JsonProcessingException {
        InterviewQuestionsDTO result = convertWithSpringAi(json, interviewQuestionsConverter, "interview-questions");
        normalizeInterviewQuestionTypes(result);
        normalizeInterviewQuestionSources(result);
        validateBean(result, "interview-questions");
        return result;
    }

    public InterviewEvaluationDTO parseInterviewEvaluation(String json) throws JsonProcessingException {
        InterviewEvaluationDTO result = convertWithSpringAi(json, interviewEvaluationConverter, "interview-evaluation");
        validateBean(result, "interview-evaluation");
        return result;
    }

    public JobDescriptionMatchResultDTO parseJobDescriptionMatchResult(String json) throws JsonProcessingException {
        JobDescriptionMatchResultDTO result = convertWithSpringAi(json, jobDescriptionMatchConverter, "jd-match");
        validateBean(result, "jd-match");
        return result;
    }

    private <T> T convertWithSpringAi(String json,
                                      BeanOutputConverter<T> converter,
                                      String schemaName) {
        try {
            return converter.convert(cleanJsonResponse(json));
        } catch (RuntimeException e) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：无法转换为目标 DTO", e);
        }
    }

    private <T> void validateBean(T result, String schemaName) {
        if (result == null) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：转换结果为空");
        }

        Set<ConstraintViolation<T>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + formatViolations(violations));
        }
    }

    private <T> String formatViolations(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
    }

    private void normalizeResumeScoreDetail(ResumeScoreResultDTO result) {
        ResumeScoreResultDTO.ScoreDetail scoreDetail = result.getScoreDetail();
        scoreDetail.setProjectScore(clampScore("resume-score", "scoreDetail", "projectScore",
                scoreDetail.getProjectScore(), 0, 40));
        scoreDetail.setSkillMatchScore(clampScore("resume-score", "scoreDetail", "skillMatchScore",
                scoreDetail.getSkillMatchScore(), 0, 20));
        scoreDetail.setContentScore(clampScore("resume-score", "scoreDetail", "contentScore",
                scoreDetail.getContentScore(), 0, 15));
        scoreDetail.setStructureScore(clampScore("resume-score", "scoreDetail", "structureScore",
                scoreDetail.getStructureScore(), 0, 15));
        scoreDetail.setExpressionScore(clampScore("resume-score", "scoreDetail", "expressionScore",
                scoreDetail.getExpressionScore(), 0, 10));
    }

    private void normalizeInterviewQuestionTypes(InterviewQuestionsDTO result) {
        if (result == null || result.getQuestions() == null) {
            return;
        }

        List<InterviewQuestionsDTO.Question> questions = result.getQuestions();
        for (int index = 0; index < questions.size(); index++) {
            InterviewQuestionsDTO.Question question = questions.get(index);
            if (question == null) {
                continue;
            }
            String originalType = question.getType();
            String normalizedType = normalizeQuestionType(originalType);
            if (!normalizedType.equals(originalType)) {
                logger.warn("AI question type normalized, schema=interview-questions, path=questions[{}].type, value={}, normalized={}",
                        index, originalType, normalizedType);
                question.setType(normalizedType);
            }
        }
    }

    private String normalizeQuestionType(String type) {
        if (type == null || type.isBlank()) {
            return "PROJECT";
        }

        String normalized = type.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (ALLOWED_QUESTION_TYPES.contains(normalized)) {
            return normalized;
        }

        return QUESTION_TYPE_ALIASES.getOrDefault(normalized, "PROJECT");
    }

    /**
     * 规整面试题来源字段，避免模型漏填或输出中文来源导致 DTO 校验失败。
     */
    private void normalizeInterviewQuestionSources(InterviewQuestionsDTO result) {
        if (result == null || result.getQuestions() == null) {
            return;
        }

        List<InterviewQuestionsDTO.Question> questions = result.getQuestions();
        for (int index = 0; index < questions.size(); index++) {
            InterviewQuestionsDTO.Question question = questions.get(index);
            if (question == null) {
                continue;
            }
            String normalizedSource = normalizeEvidenceSource(question.getEvidenceSource());
            if (!normalizedSource.equals(question.getEvidenceSource())) {
                logger.warn("AI question evidence source normalized, schema=interview-questions, path=questions[{}].evidenceSource, value={}, normalized={}",
                        index, question.getEvidenceSource(), normalizedSource);
                question.setEvidenceSource(normalizedSource);
            }
            question.setSourceNote(normalizeSourceNote(question.getSourceNote(), normalizedSource));
        }
    }

    /**
     * 将模型输出的问题来源归一化到允许枚举。
     */
    private String normalizeEvidenceSource(String evidenceSource) {
        if (evidenceSource == null || evidenceSource.isBlank()) {
            return CURRENT_RESUME_FACT;
        }

        String normalized = evidenceSource.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (ALLOWED_EVIDENCE_SOURCES.contains(normalized)) {
            return normalized;
        }
        return EVIDENCE_SOURCE_ALIASES.getOrDefault(
                normalized,
                EVIDENCE_SOURCE_ALIASES.getOrDefault(evidenceSource.trim(), CURRENT_RESUME_FACT)
        );
    }

    /**
     * 生成或裁剪问题来源说明，避免数据库和页面展示被长文本撑开。
     */
    private String normalizeSourceNote(String sourceNote, String evidenceSource) {
        String normalizedNote = sourceNote == null || sourceNote.isBlank()
                ? defaultSourceNote(evidenceSource)
                : sourceNote.trim();
        if (normalizedNote.length() <= MAX_SOURCE_NOTE_CHARS) {
            return normalizedNote;
        }
        return normalizedNote.substring(0, MAX_SOURCE_NOTE_CHARS);
    }

    /**
     * 根据来源类型给出默认中文说明。
     */
    private String defaultSourceNote(String evidenceSource) {
        if (SIMILAR_RESUME_REFERENCE.equals(evidenceSource)) {
            return "参考相似简历片段设计追问";
        }
        return "基于当前简历事实设计追问";
    }

    private int clampScore(String schemaName,
                           String path,
                           String fieldName,
                           int value,
                           int min,
                           int max) {
        if (value >= min && value <= max) {
            return value;
        }

        int clamped = Math.max(min, Math.min(max, value));
        logger.warn("AI score field out of range, schema={}, path={}.{}, value={}, normalized={}, min={}, max={}",
                schemaName, path, fieldName, value, clamped, min, max);
        return clamped;
    }

    private String cleanJsonResponse(String json) {
        String cleaned = json == null ? "" : json.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}

