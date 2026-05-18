package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptFailureReasonResult {
    private String operationName;
    private String promptVersion;
    private String reason;
    private Long count;
    private Double percentage;
}
