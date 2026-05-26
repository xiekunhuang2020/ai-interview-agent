package com.xkh.ai.interview.service.audit;

import com.xkh.ai.interview.entity.AiModelCallLogEntity;
import com.xkh.ai.interview.mapper.AiModelCallLogMapper;
import com.xkh.ai.interview.config.RequestTraceFilter;
import com.xkh.ai.interview.service.llm.AiStructuredOutputException;
import com.xkh.ai.interview.service.llm.PromptContextBudgetService;
import jakarta.validation.ValidationException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
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
    private final PromptContextBudgetService contextBudgetService;
    private final String configuredModelName;

    /**
     * 模型调用返回的官方 token usage，用于后续成本观测。
     */
    public record ModelUsage(String modelName, Integer inputTokens, Integer outputTokens, Integer totalTokens) {
    }

    /**
     * 语音转写请求的输入音频信息，用于排查 ASR 成本和识别失败。
     */
    public record AudioUsage(Long fileSizeBytes, Integer sampleRate, Long durationMs) {
    }

    /**
     * 注入模型调用审计表 Mapper，用于把每次模型调用结果写入数据库。
     */
    public AiModelCallAuditRecorder(AiModelCallLogMapper aiModelCallLogMapper,
                                    PromptContextBudgetService contextBudgetService,
                                    @Value("${spring.ai.dashscope.chat.options.model:qwen-max}") String configuredModelName) {
        this.aiModelCallLogMapper = aiModelCallLogMapper;
        this.contextBudgetService = contextBudgetService;
        this.configuredModelName = configuredModelName;
    }

    /**
     * 从 Spring AI 官方 ChatResponse metadata 中读取模型名称和 token usage。
     */
    public ModelUsage usageOf(ChatResponse response) {
        return usageOf(response, null);
    }

    /**
     * 从 Spring AI 官方 ChatResponse metadata 中读取 token usage，并优先记录本次请求指定的模型。
     */
    public ModelUsage usageOf(ChatResponse response, String requestedModelName) {
        String modelName = StringUtils.defaultIfBlank(requestedModelName, configuredModelName);
        if (response == null || response.getMetadata() == null) {
            return StringUtils.isBlank(modelName) ? null : new ModelUsage(modelName, null, null, null);
        }

        modelName = StringUtils.defaultIfBlank(requestedModelName,
                StringUtils.defaultIfBlank(response.getMetadata().getModel(), configuredModelName));
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return StringUtils.isBlank(modelName) ? null : new ModelUsage(modelName, null, null, null);
        }

        Integer inputTokens = usage.getPromptTokens();
        Integer outputTokens = usage.getCompletionTokens();
        Integer totalTokens = totalTokens(inputTokens, outputTokens, usage.getTotalTokens());
        return new ModelUsage(modelName, inputTokens, outputTokens, totalTokens);
    }

    /**
     * 记录一次带官方 token usage 和上下文预算统计的模型调用审计。
     */
    @Transactional
    public void record(String operationName,
                       String promptVersion,
                       boolean success,
                       long latencyMs,
                       String errorMessage,
                       ModelUsage usage,
                       PromptContextBudgetService.ContextUsage contextUsage) {
        record(operationName, promptVersion, success, latencyMs, errorMessage, null, usage, contextUsage);
    }

    /**
     * 记录一次带异常、官方 token usage 和上下文预算统计的模型调用审计。
     */
    @Transactional
    public void record(String operationName,
                       String promptVersion,
                       boolean success,
                       long latencyMs,
                       Throwable error,
                       ModelUsage usage,
                       PromptContextBudgetService.ContextUsage contextUsage) {
        record(operationName, promptVersion, success, latencyMs,
                error == null ? null : error.getMessage(), error, usage, contextUsage);
    }

    /**
     * 记录一次语音转写审计，ASR 没有 token usage 时保留模型名、音频大小、采样率和耗时。
     */
    @Transactional
    public void recordAudio(String operationName,
                            String promptVersion,
                            boolean success,
                            long latencyMs,
                            Throwable error,
                            ModelUsage usage,
                            AudioUsage audioUsage) {
        record(operationName, promptVersion, success, latencyMs,
                error == null ? null : error.getMessage(), error, usage, null, audioUsage);
    }

    /**
     * 写入模型调用审计记录，附带 token usage、错误文本裁剪和错误类型分类。
     */
    private void record(String operationName,
                        String promptVersion,
                        boolean success,
                        long latencyMs,
                        String errorMessage,
                        Throwable error,
                        ModelUsage usage,
                        PromptContextBudgetService.ContextUsage contextUsage) {
        record(operationName, promptVersion, success, latencyMs,
                errorMessage, error, usage, contextUsage, null);
    }

    /**
     * 写入模型调用审计记录，附带 token usage、上下文预算和语音输入元信息。
     */
    private void record(String operationName,
                        String promptVersion,
                        boolean success,
                        long latencyMs,
                        String errorMessage,
                        Throwable error,
                        ModelUsage usage,
                        PromptContextBudgetService.ContextUsage contextUsage,
                        AudioUsage audioUsage) {
        try {
            AiModelCallLogEntity log = new AiModelCallLogEntity();
            log.setTraceId(MDC.get(RequestTraceFilter.TRACE_ID_KEY));
            log.setOperationName(operationName);
            log.setPromptVersion(promptVersion);
            fillUsage(log, usage);
            fillAudioUsage(log, audioUsage);
            fillContextUsage(log, contextUsage);
            fillInputBudget(log, operationName, usage, contextUsage);
            log.setSuccess(success ? 1 : 0);
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
     * 将语音输入元信息写入审计记录，方便看板区分不同 ASR 请求规模。
     */
    private void fillAudioUsage(AiModelCallLogEntity log, AudioUsage audioUsage) {
        if (audioUsage == null) {
            return;
        }
        log.setAudioFileSizeBytes(audioUsage.fileSizeBytes());
        log.setAudioSampleRate(audioUsage.sampleRate());
        log.setAudioDurationMs(audioUsage.durationMs());
    }

    /**
     * 将官方 usage 写入审计实体，usage 为空时保持兼容旧记录。
     */
    private void fillUsage(AiModelCallLogEntity log, ModelUsage usage) {
        if (usage == null) {
            return;
        }
        log.setModelName(usage.modelName());
        log.setInputTokens(usage.inputTokens());
        log.setOutputTokens(usage.outputTokens());
        log.setTotalTokens(usage.totalTokens());
    }

    /**
     * 将上下文预算统计写入审计实体，便于看板观察 Prompt 长度和裁剪次数。
     */
    private void fillContextUsage(AiModelCallLogEntity log, PromptContextBudgetService.ContextUsage contextUsage) {
        if (contextUsage == null) {
            return;
        }
        log.setPromptChars(contextUsage.promptChars());
        log.setClippedChars(contextUsage.clippedChars());
        log.setContextClipped(contextUsage.clipped() ? 1 : 0);
    }

    /**
     * 根据真实输入 Token 和场景预算判断本次调用是否超预算、是否未被裁剪策略覆盖。
     */
    private void fillInputBudget(AiModelCallLogEntity log,
                                 String operationName,
                                 ModelUsage usage,
                                 PromptContextBudgetService.ContextUsage contextUsage) {
        Integer inputTokenBudget = contextBudgetService.inputTokenBudgetOf(operationName);
        if (inputTokenBudget == null || inputTokenBudget <= 0) {
            return;
        }
        log.setInputTokenBudget(inputTokenBudget);
        if (usage == null || usage.inputTokens() == null) {
            return;
        }
        int overBudget = Math.max(0, usage.inputTokens() - inputTokenBudget);
        boolean clipped = contextUsage != null && contextUsage.clipped();
        log.setInputTokenOverBudget(overBudget);
        log.setBudgetExceeded(overBudget > 0 ? 1 : 0);
        log.setBudgetUncovered(overBudget > 0 && !clipped ? 1 : 0);
    }

    /**
     * 读取官方 totalTokens，缺失时用输入和输出 token 做兼容计算。
     */
    private Integer totalTokens(Integer inputTokens, Integer outputTokens, Integer officialTotalTokens) {
        if (officialTotalTokens != null) {
            return officialTotalTokens;
        }
        if (inputTokens == null && outputTokens == null) {
            return null;
        }
        return (inputTokens == null ? 0 : inputTokens) + (outputTokens == null ? 0 : outputTokens);
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

