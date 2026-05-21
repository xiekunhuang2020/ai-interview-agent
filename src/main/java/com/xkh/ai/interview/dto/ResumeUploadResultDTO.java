package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResumeUploadResultDTO {
    private String resumeId;
    private ResumeScoreResultDTO scoreResult;
}

