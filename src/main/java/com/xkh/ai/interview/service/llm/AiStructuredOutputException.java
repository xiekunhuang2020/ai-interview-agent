package com.xkh.ai.interview.service.llm;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public class AiStructuredOutputException extends RuntimeException {

    private final String schemaName;
    private final String fieldPath;
    private final String failureReason;

    /**
     * 创建带 schema、字段路径和失败原因的结构化输出异常，方便前端和审计定位问题。
     */
    public AiStructuredOutputException(String schemaName, String fieldPath, String failureReason, Throwable cause) {
        super(buildMessage(schemaName, fieldPath, failureReason), cause);
        this.schemaName = StringUtils.defaultString(schemaName);
        this.fieldPath = StringUtils.defaultString(fieldPath);
        this.failureReason = StringUtils.defaultString(failureReason);
    }

    /**
     * 组装前端和审计都能直接阅读的结构化输出失败文案。
     */
    private static String buildMessage(String schemaName, String fieldPath, String failureReason) {
        return "结构化输出失败：schema=" + StringUtils.defaultIfBlank(schemaName, "unknown")
                + "，字段=" + StringUtils.defaultIfBlank(fieldPath, "unknown")
                + "，原因=" + StringUtils.defaultIfBlank(failureReason, "unknown");
    }
}
