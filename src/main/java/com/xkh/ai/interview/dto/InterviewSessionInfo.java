package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionInfo {
    private String resumeId;
    private String status;
    private String statusText;
    private String currentStage;
    private String currentStageText;
    private String failedStage;
    private String failedReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
