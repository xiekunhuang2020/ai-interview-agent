package com.xkh.ai.interview.dto;

import lombok.Data;

import java.util.List;

/**
 * Prompt/RAG 评测样例配置，用固定简历、JD 和参考简历驱动官方 Evaluator 回归评测。
 */
@Data
public class PromptRagEvaluationCaseDTO {
    private String caseId;
    private String name;
    private String resumeFile;
    private String jobDescriptionFile;
    private List<String> referenceResumeFiles;
    private Integer topK;
}
