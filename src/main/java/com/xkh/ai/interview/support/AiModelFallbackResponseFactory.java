package com.xkh.ai.interview.support;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AiModelFallbackResponseFactory {

    public Optional<String> fallbackFor(String operationName) {
        return switch (operationName) {
            case "resume-analysis" -> Optional.of(resumeAnalysisFallback());
            case "interview-question-generation", "rag-interview-question-generation" ->
                    Optional.of(interviewQuestionsFallback());
            case "answer-evaluation" -> Optional.of(answerEvaluationFallback());
            case "jd-match" -> Optional.of(jobDescriptionMatchFallback());
            default -> Optional.empty();
        };
    }

    private String resumeAnalysisFallback() {
        return """
                {
                  "overallScore": 0,
                  "scoreDetail": {
                    "projectScore": 0,
                    "skillMatchScore": 0,
                    "contentScore": 0,
                    "structureScore": 0,
                    "expressionScore": 0
                  },
                  "summary": "AI 模型服务暂时不可用，当前返回降级结果，请稍后重试获取完整简历分析。",
                  "strengths": [],
                  "suggestions": [
                    {
                      "category": "系统",
                      "priority": "高",
                      "issue": "模型服务暂时不可用",
                      "recommendation": "请稍后重试，或检查模型网关、API Key、网络和限流状态。"
                    }
                  ]
                }
                """;
    }

    private String interviewQuestionsFallback() {
        return """
                {
                  "questions": []
                }
                """;
    }

    private String answerEvaluationFallback() {
        return """
                {
                  "sessionId": "fallback-session",
                  "totalQuestions": 0,
                  "overallScore": 0,
                  "categoryScores": [],
                  "questionDetails": [],
                  "overallFeedback": "AI 模型服务暂时不可用，当前返回降级评估结果，请稍后重试获取完整面试反馈。",
                  "strengths": [],
                  "improvements": ["请稍后重试，或检查模型网关、API Key、网络和限流状态。"],
                  "referenceAnswers": []
                }
                """;
    }

    private String jobDescriptionMatchFallback() {
        return """
                {
                  "overallScore": 0,
                  "matchLevel": "匹配度较低",
                  "summary": "AI 模型服务暂时不可用，当前返回降级匹配结果，请稍后重试获取完整 JD 匹配分析。",
                  "matchedSkills": [],
                  "missingSkills": [
                    {
                      "skill": "模型服务",
                      "importance": "高",
                      "suggestion": "请稍后重试，或检查模型网关、API Key、网络和限流状态。"
                    }
                  ],
                  "interviewFocus": [],
                  "risks": ["模型服务暂时不可用，无法完成真实岗位匹配分析。"],
                  "learningSuggestions": []
                }
                """;
    }
}
