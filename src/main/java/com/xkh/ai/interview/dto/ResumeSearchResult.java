package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResumeSearchResult {
    private String resumeId;
    private String fileName;
    private String resumeText;
}
