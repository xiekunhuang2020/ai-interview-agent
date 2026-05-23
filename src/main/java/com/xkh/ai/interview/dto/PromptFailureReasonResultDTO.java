package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptFailureReasonResultDTO {
    private String operationName;
    private String promptVersion;
    private String errorType;
    private String reason;
    private Long count;
    private Double percentage;
}

