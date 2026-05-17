package com.xkh.ai.interview.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.service.dto.InterviewEvaluation;
import com.xkh.ai.interview.service.dto.InterviewQuestions;
import com.xkh.ai.interview.service.dto.JobDescriptionMatchResult;
import com.xkh.ai.interview.service.dto.ResumeScoreResult;
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

    private final Validator validator;
    private final BeanOutputConverter<ResumeScoreResult> resumeScoreConverter;
    private final BeanOutputConverter<InterviewQuestions> interviewQuestionsConverter;
    private final BeanOutputConverter<InterviewEvaluation> interviewEvaluationConverter;
    private final BeanOutputConverter<JobDescriptionMatchResult> jobDescriptionMatchConverter;

    public AiJsonResponseParser(ObjectMapper objectMapper, Validator validator) {
        ObjectMapper strictObjectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, false);
        this.validator = validator;
        this.resumeScoreConverter = new BeanOutputConverter<>(ResumeScoreResult.class, strictObjectMapper);
        this.interviewQuestionsConverter = new BeanOutputConverter<>(InterviewQuestions.class, strictObjectMapper);
        this.interviewEvaluationConverter = new BeanOutputConverter<>(InterviewEvaluation.class, strictObjectMapper);
        this.jobDescriptionMatchConverter = new BeanOutputConverter<>(JobDescriptionMatchResult.class, strictObjectMapper);
    }

    public ResumeScoreResult parseResumeScoreResult(String json) throws JsonProcessingException {
        ResumeScoreResult result = convertWithSpringAi(json, resumeScoreConverter, "resume-score");
        validateBean(result, "resume-score");
        normalizeResumeScoreDetail(result);
        return result;
    }

    public InterviewQuestions parseInterviewQuestions(String json) throws JsonProcessingException {
        InterviewQuestions result = convertWithSpringAi(json, interviewQuestionsConverter, "interview-questions");
        normalizeInterviewQuestionTypes(result);
        validateBean(result, "interview-questions");
        return result;
    }

    public InterviewEvaluation parseInterviewEvaluation(String json) throws JsonProcessingException {
        InterviewEvaluation result = convertWithSpringAi(json, interviewEvaluationConverter, "interview-evaluation");
        validateBean(result, "interview-evaluation");
        return result;
    }

    public JobDescriptionMatchResult parseJobDescriptionMatchResult(String json) throws JsonProcessingException {
        JobDescriptionMatchResult result = convertWithSpringAi(json, jobDescriptionMatchConverter, "jd-match");
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

    private void normalizeResumeScoreDetail(ResumeScoreResult result) {
        ResumeScoreResult.ScoreDetail scoreDetail = result.getScoreDetail();
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

    private void normalizeInterviewQuestionTypes(InterviewQuestions result) {
        if (result == null || result.getQuestions() == null) {
            return;
        }

        List<InterviewQuestions.Question> questions = result.getQuestions();
        for (int index = 0; index < questions.size(); index++) {
            InterviewQuestions.Question question = questions.get(index);
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
