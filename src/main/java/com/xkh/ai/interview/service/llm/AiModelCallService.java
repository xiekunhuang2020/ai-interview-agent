package com.xkh.ai.interview.service.llm;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.xkh.ai.interview.dto.InterviewEvaluationDTO;
import com.xkh.ai.interview.dto.InterviewQuestionsDTO;
import com.xkh.ai.interview.dto.JobDescriptionMatchResultDTO;
import com.xkh.ai.interview.dto.ResumeScoreResultDTO;
import com.xkh.ai.interview.service.audit.AiModelCallAuditRecorder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AiModelCallService {

    private static final Logger logger = LoggerFactory.getLogger(AiModelCallService.class);
    private static final int MAX_ERROR_REASON_LENGTH = 240;

    private final ChatClient chatClient;
    private final PromptVersionRegistry promptVersionRegistry;
    private final AiModelCallAuditRecorder auditRecorder;
    private final Validator validator;

    /**
     * 使用 Spring AI 提供的 ChatClient 作为模型调用入口。
     * 本类只补充业务侧需要的 Prompt 版本、调用审计和异常映射，不替代框架重试、流式和工具调用能力。
     */
    public AiModelCallService(ChatClient.Builder chatClientBuilder,
                              PromptVersionRegistry promptVersionRegistry,
                              AiModelCallAuditRecorder auditRecorder,
                              Validator validator) {
        this.chatClient = chatClientBuilder.build();
        this.promptVersionRegistry = promptVersionRegistry;
        this.auditRecorder = auditRecorder;
        this.validator = validator;
    }

    /**
     * 调用大模型并记录审计，适用于不需要 RAG Advisor 的普通模型任务。
     */
    public String call(String operationName, List<Message> messages, double temperature) {
        return call(operationName, messages, temperature, List.of());
    }

    /**
     * 调用大模型并记录审计，advisors 由 Spring AI 官方 Advisor 机制处理。
     */
    public String call(String operationName, List<Message> messages, double temperature, List<Advisor> advisors) {
        String promptVersion = promptVersionRegistry.versionOf(operationName);
        Prompt prompt = new Prompt(messages, DashScopeChatOptions.builder()
                .temperature(temperature)
                .build());

        long start = System.currentTimeMillis();
        try {
            String text = doCall(prompt, advisors);
            if (StringUtils.isBlank(text)) {
                throw new IllegalStateException("AI 模型返回空内容");
            }
            long latencyMs = System.currentTimeMillis() - start;
            logger.info("AI model call succeeded, operation={}, promptVersion={}, latencyMs={}",
                    operationName, promptVersion, latencyMs);
            auditRecorder.record(operationName, promptVersion, true, 1, latencyMs, (String) null);
            return text;
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - start;
            logger.warn("AI model call failed after Spring AI retry, operation={}, promptVersion={}, latencyMs={}, error={}",
                    operationName, promptVersion, latencyMs, e.getMessage());
            auditRecorder.record(operationName, promptVersion, false, 1, latencyMs, e);
            throw new AiModelCallException("AI 模型调用失败，operation=" + operationName, e);
        }
    }

    /**
     * 调用大模型并使用 Spring AI 官方 entity 转换为 DTO，适用于无 RAG Advisor 的结构化任务。
     */
    public <T> T callEntity(String operationName,
                            List<Message> messages,
                            double temperature,
                            Class<T> targetType) {
        return callEntity(operationName, messages, temperature, List.of(), targetType);
    }

    /**
     * 调用大模型并使用 Spring AI 官方 entity 转换为 DTO，advisors 由 Spring AI 官方机制注入。
     */
    public <T> T callEntity(String operationName,
                            List<Message> messages,
                            double temperature,
                            List<Advisor> advisors,
                            Class<T> targetType) {
        String promptVersion = promptVersionRegistry.versionOf(operationName);
        Prompt prompt = new Prompt(messages, DashScopeChatOptions.builder()
                .temperature(temperature)
                .build());

        long start = System.currentTimeMillis();
        try {
            T result = doCallEntity(prompt, advisors, targetType);
            validateEntity(result, targetType);
            long latencyMs = System.currentTimeMillis() - start;
            logger.info("AI structured call succeeded, operation={}, promptVersion={}, targetType={}, latencyMs={}",
                    operationName, promptVersion, targetType.getSimpleName(), latencyMs);
            auditRecorder.record(operationName, promptVersion, true, 1, latencyMs, (String) null);
            return result;
        } catch (AiStructuredOutputException e) {
            long latencyMs = System.currentTimeMillis() - start;
            logger.warn("AI structured output failed, operation={}, promptVersion={}, targetType={}, latencyMs={}, error={}",
                    operationName, promptVersion, targetType.getSimpleName(), latencyMs, e.getMessage());
            auditRecorder.record(operationName, promptVersion, false, 1, latencyMs, e);
            throw e;
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - start;
            logger.warn("AI model call failed after Spring AI retry, operation={}, promptVersion={}, latencyMs={}, error={}",
                    operationName, promptVersion, latencyMs, e.getMessage());
            auditRecorder.record(operationName, promptVersion, false, 1, latencyMs, e);
            throw new AiModelCallException("AI 模型调用失败，operation=" + operationName, e);
        }
    }

    /**
     * 按是否存在 Advisor 选择 Spring AI 调用链，避免业务层自己处理 RAG 注入细节。
     */
    private String doCall(Prompt prompt, List<Advisor> advisors) {
        if (advisors == null || advisors.isEmpty()) {
            return chatClient.prompt(prompt)
                    .call()
                    .content();
        }
        return chatClient.prompt(prompt)
                .advisors(advisors)
                .call()
                .content();
    }

    /**
     * 使用 Spring AI 官方 ChatClient.entity 完成结构化 DTO 转换。
     */
    private <T> T doCallEntity(Prompt prompt, List<Advisor> advisors, Class<T> targetType) {
        try {
            if (advisors == null || advisors.isEmpty()) {
                return chatClient.prompt(prompt)
                        .call()
                        .entity(targetType);
            }
            return chatClient.prompt(prompt)
                    .advisors(advisors)
                    .call()
                    .entity(targetType);
        } catch (RuntimeException e) {
            if (looksLikeModelGatewayFailure(e)) {
                throw e;
            }
            throw toStructuredOutputException(targetType, e);
        }
    }

    /**
     * 使用 Jakarta Bean Validation 校验 DTO 注解，业务规则不再写成手工 JSON parser。
     */
    private <T> void validateEntity(T result, Class<T> targetType) {
        String schemaName = schemaName(targetType);
        if (result == null) {
            throw new AiStructuredOutputException(schemaName, "root", "Spring AI entity 转换结果为空", null);
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
     * 选择第一个字段校验错误，用于前端突出展示字段路径。
     */
    private <T> ConstraintViolation<T> firstViolation(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .min(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .orElseThrow();
    }

    /**
     * 将 DTO 注解校验错误整理为短文本，方便审计表和前端展示。
     */
    private <T> String formatViolations(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
    }

    /**
     * 将 Spring AI entity 转换异常映射为业务可读的结构化输出错误。
     */
    private AiStructuredOutputException toStructuredOutputException(Class<?> targetType, RuntimeException error) {
        return new AiStructuredOutputException(
                schemaName(targetType),
                "root",
                truncateReason(StringUtils.defaultIfBlank(error.getMessage(), "Spring AI entity 无法转换为目标 DTO")),
                error
        );
    }

    /**
     * 判断异常是否更像模型网关失败，避免把超时、限流和 HTTP 错误误判为结构化输出错误。
     */
    private boolean looksLikeModelGatewayFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String className = current.getClass().getName().toLowerCase(Locale.ROOT);
            String message = StringUtils.defaultString(current.getMessage()).toLowerCase(Locale.ROOT);
            if (className.contains("restclient") || className.contains("dashscope")
                    || className.contains("retry") || className.contains("web.client")
                    || containsAny(message, "http 4", "http 5", "timeout", "timed out",
                    "rate limit", "too many requests", "quota", "api key", "dashscope")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 判断文本是否包含任意关键词，忽略大小写。
     */
    private boolean containsAny(String text, String... keywords) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalizedText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据 DTO 类型生成看板和前端使用的 schema 名称。
     */
    private String schemaName(Class<?> targetType) {
        if (ResumeScoreResultDTO.class.equals(targetType)) {
            return "resume-score";
        }
        if (InterviewQuestionsDTO.class.equals(targetType)) {
            return "interview-questions";
        }
        if (InterviewEvaluationDTO.class.equals(targetType)) {
            return "interview-evaluation";
        }
        if (JobDescriptionMatchResultDTO.class.equals(targetType)) {
            return "jd-match";
        }
        return targetType.getSimpleName();
    }

    /**
     * 裁剪结构化转换失败原因，避免底层异常文本撑开页面或审计字段。
     */
    private String truncateReason(String message) {
        if (message == null || message.length() <= MAX_ERROR_REASON_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_REASON_LENGTH);
    }

}
