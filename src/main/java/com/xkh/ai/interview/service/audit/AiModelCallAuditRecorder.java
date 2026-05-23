package com.xkh.ai.interview.service.audit;

import com.xkh.ai.interview.entity.AiModelCallLogEntity;
import com.xkh.ai.interview.mapper.AiModelCallLogMapper;
import com.xkh.ai.interview.config.RequestTraceFilter;
import com.xkh.ai.interview.service.llm.AiStructuredOutputException;
import jakarta.validation.ValidationException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

@Component
public class AiModelCallAuditRecorder {

    private static final Logger logger = LoggerFactory.getLogger(AiModelCallAuditRecorder.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;
    private static final String ERROR_TYPE_TIMEOUT = "TIMEOUT";
    private static final String ERROR_TYPE_RATE_LIMIT = "RATE_LIMIT";
    private static final String ERROR_TYPE_MODEL_ERROR = "MODEL_ERROR";
    private static final String ERROR_TYPE_STRUCTURED_OUTPUT_ERROR = "STRUCTURED_OUTPUT_ERROR";
    private static final String ERROR_TYPE_VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String ERROR_TYPE_EMPTY_RESPONSE = "EMPTY_RESPONSE";
    private static final String ERROR_TYPE_UNKNOWN = "UNKNOWN";

    private final AiModelCallLogMapper aiModelCallLogMapper;

    /**
     * 注入模型调用审计表 Mapper，用于把每次模型调用结果写入数据库。
     */
    public AiModelCallAuditRecorder(AiModelCallLogMapper aiModelCallLogMapper) {
        this.aiModelCallLogMapper = aiModelCallLogMapper;
    }

    /**
     * 记录一次模型调用审计，审计失败只打印日志，不影响主业务流程。
     */
    @Transactional
    public void record(String operationName,
                       String promptVersion,
                       boolean success,
                       int attemptCount,
                       long latencyMs,
                       String errorMessage) {
        record(operationName, promptVersion, success, attemptCount, latencyMs, errorMessage, null);
    }

    /**
     * 记录一次模型调用审计，并根据异常类型自动生成错误分类。
     */
    @Transactional
    public void record(String operationName,
                       String promptVersion,
                       boolean success,
                       int attemptCount,
                       long latencyMs,
                       Throwable error) {
        record(operationName, promptVersion, success, attemptCount, latencyMs,
                error == null ? null : error.getMessage(), error);
    }

    /**
     * 写入模型调用审计记录，内部统一处理错误文本裁剪和错误类型分类。
     */
    private void record(String operationName,
                        String promptVersion,
                        boolean success,
                        int attemptCount,
                        long latencyMs,
                        String errorMessage,
                        Throwable error) {
        try {
            AiModelCallLogEntity log = new AiModelCallLogEntity();
            log.setTraceId(MDC.get(RequestTraceFilter.TRACE_ID_KEY));
            log.setOperationName(operationName);
            log.setPromptVersion(promptVersion);
            log.setSuccess(success ? 1 : 0);
            log.setFallbackUsed(0);
            log.setAttemptCount(attemptCount);
            log.setLatencyMs(latencyMs);
            log.setErrorMessage(truncate(errorMessage));
            log.setErrorType(success ? null : classifyError(error, errorMessage));
            aiModelCallLogMapper.insert(log);
        } catch (Exception e) {
            logger.warn("Failed to record AI model call audit, operation={}, error={}",
                    operationName, e.getMessage());
        }
    }

    /**
     * 裁剪过长的错误信息，避免异常堆栈直接撑爆审计字段。
     */
    private String truncate(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    /**
     * 按异常类型和错误文本归类模型失败原因，便于看板聚合统计。
     */
    private String classifyError(Throwable error, String errorMessage) {
        if (isCausedBy(error, AiStructuredOutputException.class)) {
            return ERROR_TYPE_STRUCTURED_OUTPUT_ERROR;
        }
        if (isCausedBy(error, ValidationException.class)) {
            return ERROR_TYPE_VALIDATION_ERROR;
        }
        if (isCausedBy(error, TimeoutException.class) || isCausedBy(error, SocketTimeoutException.class)
                || containsAny(errorMessage, "timeout", "timed out", "read timed out", "connect timed out")) {
            return ERROR_TYPE_TIMEOUT;
        }
        if (containsAny(errorMessage, "429", "rate limit", "too many requests", "throttl", "quota", "qps")) {
            return ERROR_TYPE_RATE_LIMIT;
        }
        if (containsAny(errorMessage, "空内容", "empty response", "empty content", "blank response")) {
            return ERROR_TYPE_EMPTY_RESPONSE;
        }
        if (looksLikeModelError(error, errorMessage)) {
            return ERROR_TYPE_MODEL_ERROR;
        }
        return ERROR_TYPE_UNKNOWN;
    }

    /**
     * 判断异常链路中是否包含指定异常类型。
     */
    private boolean isCausedBy(Throwable error, Class<? extends Throwable> targetType) {
        Throwable current = error;
        while (current != null) {
            if (targetType.isAssignableFrom(current.getClass())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 判断错误文本是否包含任意关键词，忽略大小写。
     */
    private boolean containsAny(String message, String... keywords) {
        if (StringUtils.isBlank(message)) {
            return false;
        }
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalizedMessage.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断异常是否来自模型服务或 HTTP 模型网关。
     */
    private boolean looksLikeModelError(Throwable error, String errorMessage) {
        if (containsAny(errorMessage, "dashscope", "http 4", "http 5", "invalidparameter", "model", "api key")) {
            return true;
        }
        Throwable current = error;
        while (current != null) {
            String className = current.getClass().getName().toLowerCase(Locale.ROOT);
            if (className.contains("springframework.ai") || className.contains("dashscope")
                    || className.contains("restclient")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

