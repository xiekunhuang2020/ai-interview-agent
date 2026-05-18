package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResumeUploadResult {
    private String resumeId;
    private ResumeScoreResult scoreResult;
}
