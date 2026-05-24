package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptMetricsResultDTO {
    private String operationName;
    private String promptVersion;
    private String modelNames;
    private Long totalCalls;
    private Long successCalls;
    private Long failedCalls;
    private Double successRate;
    private Double avgLatencyMs;
    private Long maxLatencyMs;
    private Double avgAttemptCount;
    private Long tokenSampleCalls;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private Long totalTokens;
    private Double avgTotalTokens;
}

