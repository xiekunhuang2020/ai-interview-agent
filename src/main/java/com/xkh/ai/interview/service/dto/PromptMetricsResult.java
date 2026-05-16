package com.xkh.ai.interview.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptMetricsResult {
    private String operationName;
    private String promptVersion;
    private Long totalCalls;
    private Long successCalls;
    private Long failedCalls;
    private Long fallbackCalls;
    private Double successRate;
    private Double fallbackRate;
    private Double avgLatencyMs;
    private Long maxLatencyMs;
    private Double avgAttemptCount;
}
