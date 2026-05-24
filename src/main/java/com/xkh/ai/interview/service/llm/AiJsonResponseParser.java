package com.xkh.ai.interview.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.dto.InterviewEvaluationDTO;
import com.xkh.ai.interview.dto.InterviewQuestionsDTO;
import com.xkh.ai.interview.dto.JobDescriptionMatchResultDTO;
import com.xkh.ai.interview.dto.ResumeScoreResultDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AiJsonResponseParser {

    private final Validator validator;
    private final BeanOutputConverter<ResumeScoreResultDTO> resumeScoreConverter;
    private final BeanOutputConverter<InterviewQuestionsDTO> interviewQuestionsConverter;
    private final BeanOutputConverter<InterviewEvaluationDTO> interviewEvaluationConverter;
    private final BeanOutputConverter<JobDescriptionMatchResultDTO> jobDescriptionMatchConverter;

    /**
     * 创建结构化输出解析器，底层转换交给 Spring AI BeanOutputConverter。
     */
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

    /**
     * 解析简历评分模型输出，并用 DTO 注解校验结构。
     */
    public ResumeScoreResultDTO parseResumeScoreResult(String json) throws JsonProcessingException {
        ResumeScoreResultDTO result = convertWithSpringAi(json, resumeScoreConverter, "resume-score");
        validateBean(result, "resume-score");
        return result;
    }

    /**
     * 解析面试题模型输出，并用 DTO 注解校验结构。
     */
    public InterviewQuestionsDTO parseInterviewQuestions(String json) throws JsonProcessingException {
        InterviewQuestionsDTO result = convertWithSpringAi(json, interviewQuestionsConverter, "interview-questions");
        validateBean(result, "interview-questions");
        return result;
    }

    /**
     * 解析回答评估模型输出，并用 DTO 注解校验结构。
     */
    public InterviewEvaluationDTO parseInterviewEvaluation(String json) throws JsonProcessingException {
        InterviewEvaluationDTO result = convertWithSpringAi(json, interviewEvaluationConverter, "interview-evaluation");
        validateBean(result, "interview-evaluation");
        return result;
    }

    /**
     * 解析岗位匹配模型输出，并用 DTO 注解校验结构。
     */
    public JobDescriptionMatchResultDTO parseJobDescriptionMatchResult(String json) throws JsonProcessingException {
        JobDescriptionMatchResultDTO result = convertWithSpringAi(json, jobDescriptionMatchConverter, "jd-match");
        validateBean(result, "jd-match");
        return result;
    }

    /**
     * 使用 Spring AI Converter 将 JSON 文本转换为目标 DTO。
     */
    private <T> T convertWithSpringAi(String json,
                                      BeanOutputConverter<T> converter,
                                      String schemaName) {
        try {
            return converter.convert(cleanJsonResponse(json));
        } catch (RuntimeException e) {
            StructuredOutputError error = diagnoseConversionFailure(e);
            throw new AiStructuredOutputException(schemaName, error.fieldPath(), error.reason(), e);
        }
    }

    /**
     * 调用 Jakarta Bean Validation 校验 DTO 注解约束。
     */
    private <T> void validateBean(T result, String schemaName) {
        if (result == null) {
            throw new AiStructuredOutputException(schemaName, "root", "转换结果为空", null);
        }

        Set<ConstraintViolation<T>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> firstViolation = firstViolation(violations);
            throw new AiStructuredOutputException(
                    schemaName,
                    firstViolation.getPropertyPath().toString(),
                    formatViolations(violations),
                    null
            );
        }
    }

    /**
     * 从 Jackson 异常链中提取字段路径和转换失败原因。
     */
    private StructuredOutputError diagnoseConversionFailure(Throwable error) {
        JsonMappingException mappingException = findCause(error, JsonMappingException.class);
        if (mappingException == null) {
            return new StructuredOutputError("root", "无法转换为目标 DTO");
        }
        String fieldPath = mappingException.getPath().stream()
                .map(reference -> reference.getFieldName() == null
                        ? "[" + reference.getIndex() + "]"
                        : reference.getFieldName())
                .collect(Collectors.joining("."));
        return new StructuredOutputError(
                fieldPath.isBlank() ? "root" : fieldPath,
                simplifyJsonError(mappingException.getOriginalMessage())
        );
    }

    /**
     * 在异常链中查找指定类型的异常。
     */
    private <T extends Throwable> T findCause(Throwable error, Class<T> targetType) {
        Throwable current = error;
        while (current != null) {
            if (targetType.isAssignableFrom(current.getClass())) {
                return targetType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 取字段校验失败中的第一个错误，用于填充单独的字段路径。
     */
    private <T> ConstraintViolation<T> firstViolation(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .min(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .orElseThrow();
    }

    /**
     * 将 Bean Validation 的错误路径和原因整理成面向日志和前端的短文本。
     */
    private <T> String formatViolations(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
    }

    /**
     * 清理模型常见的 Markdown JSON 代码块包裹。
     */
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

    /**
     * 裁剪 Jackson 原始异常，避免前端暴露过长的底层类型信息。
     */
    private String simplifyJsonError(String message) {
        if (message == null || message.isBlank()) {
            return "无法转换为目标 DTO";
        }
        int lineInfoIndex = message.indexOf(" at [Source:");
        String simplified = lineInfoIndex > 0 ? message.substring(0, lineInfoIndex) : message;
        return simplified.length() > 160 ? simplified.substring(0, 160) : simplified;
    }

    private record StructuredOutputError(String fieldPath, String reason) {
    }
}
