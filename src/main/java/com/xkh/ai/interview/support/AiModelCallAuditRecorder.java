package com.xkh.ai.interview.support;

import com.xkh.ai.interview.entity.AiModelCallLog;
import com.xkh.ai.interview.mapper.AiModelCallLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AiModelCallAuditRecorder {

    private static final Logger logger = LoggerFactory.getLogger(AiModelCallAuditRecorder.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final AiModelCallLogMapper aiModelCallLogMapper;

    public AiModelCallAuditRecorder(AiModelCallLogMapper aiModelCallLogMapper) {
        this.aiModelCallLogMapper = aiModelCallLogMapper;
    }

    @Transactional
    public void record(String operationName,
                       String promptVersion,
                       boolean success,
                       boolean fallbackUsed,
                       int attemptCount,
                       long latencyMs,
                       String errorMessage) {
        try {
            AiModelCallLog log = new AiModelCallLog();
            log.setTraceId(MDC.get(RequestTraceFilter.TRACE_ID_KEY));
            log.setOperationName(operationName);
            log.setPromptVersion(promptVersion);
            log.setSuccess(success ? 1 : 0);
            log.setFallbackUsed(fallbackUsed ? 1 : 0);
            log.setAttemptCount(attemptCount);
            log.setLatencyMs(latencyMs);
            log.setErrorMessage(truncate(errorMessage));
            aiModelCallLogMapper.insert(log);
        } catch (Exception e) {
            logger.warn("Failed to record AI model call audit, operation={}, error={}",
                    operationName, e.getMessage());
        }
    }

    private String truncate(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
