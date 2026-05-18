package com.xkh.ai.interview.dto;

import lombok.Data;

@Data
public class JobDescriptionRequest {
    private String jobDescription;
    private Integer topK = 5;
}
