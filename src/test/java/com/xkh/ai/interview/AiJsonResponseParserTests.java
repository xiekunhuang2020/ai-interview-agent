package com.xkh.ai.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.service.dto.InterviewQuestions;
import com.xkh.ai.interview.service.dto.JobDescriptionMatchResult;
import com.xkh.ai.interview.service.dto.ResumeScoreResult;
import com.xkh.ai.interview.support.AiJsonResponseParser;
import com.xkh.ai.interview.support.AiStructuredOutputException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiJsonResponseParserTests {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final AiJsonResponseParser parser = new AiJsonResponseParser(new ObjectMapper(), VALIDATOR);

    @Test
    void parsesResumeScoreResultFromMarkdownJsonBlock() throws Exception {
        String response = """
                ```json
                {
                  "overallScore": 82,
                  "scoreDetail": {
                    "projectScore": 32,
                    "skillMatchScore": 18,
                    "contentScore": 13,
                    "structureScore": 11,
                    "expressionScore": 8
                  },
                  "summary": "项目经验较完整，但量化结果仍可加强。",
                  "strengths": ["技术栈完整"],
                  "suggestions": [
                    {
                      "category": "项目",
                      "priority": "高",
                      "issue": "缺少量化结果",
                      "recommendation": "补充 RT、QPS 或成本收益指标"
                    }
                  ]
                }
                ```
                """;

        ResumeScoreResult result = parser.parseResumeScoreResult(response);

        assertEquals(82, result.getOverallScore());
        assertEquals(32, result.getScoreDetail().getProjectScore());
        assertEquals("技术栈完整", result.getStrengths().get(0));
        assertEquals("补充 RT、QPS 或成本收益指标", result.getSuggestions().get(0).getRecommendation());
    }

    @Test
    void parsesJobDescriptionMatchResult() throws Exception {
        String response = """
                {
                  "overallScore": 78,
                  "matchLevel": "较匹配",
                  "summary": "候选人具备 Java 后端和 RAG 雏形经验，但 Agent 工具调用证据不足。",
                  "matchedSkills": [
                    {
                      "skill": "Spring Boot",
                      "evidence": "项目中使用 Spring Boot 构建后端服务",
                      "score": 85
                    }
                  ],
                  "missingSkills": [
                    {
                      "skill": "工具调用",
                      "importance": "高",
                      "suggestion": "补充 Agent Tool 设计与异常处理实践"
                    }
                  ],
                  "interviewFocus": ["追问 Agent 编排边界"],
                  "risks": ["AI 工程化经验表达不足"],
                  "learningSuggestions": ["补充函数调用和结构化输出治理"]
                }
                """;

        JobDescriptionMatchResult result = parser.parseJobDescriptionMatchResult(response);

        assertEquals(78, result.getOverallScore());
        assertEquals("较匹配", result.getMatchLevel());
        assertEquals("Spring Boot", result.getMatchedSkills().get(0).getSkill());
        assertEquals("工具调用", result.getMissingSkills().get(0).getSkill());
        assertEquals("追问 Agent 编排边界", result.getInterviewFocus().get(0));
    }

    @Test
    void rejectsInterviewQuestionsWithoutQuestionsArray() {
        String response = "{\"summary\":\"缺少 questions 字段\"}";

        assertThrows(AiStructuredOutputException.class,
                () -> parser.parseInterviewQuestions(response));
    }

    @Test
    void rejectsUnknownFieldsByStrictDtoConversion() {
        String response = """
                {
                  "questions": [
                    {
                      "question": "请介绍你的项目。",
                      "type": "PROJECT",
                      "category": "项目经历",
                      "extra": "不允许的字段"
                    }
                  ]
                }
                """;

        assertThrows(AiStructuredOutputException.class,
                () -> parser.parseInterviewQuestions(response));
    }

    @Test
    void rejectsResumeScoreOutsideAllowedRange() {
        String response = """
                {
                  "overallScore": 101,
                  "scoreDetail": {
                    "projectScore": 32,
                    "skillMatchScore": 18,
                    "contentScore": 13,
                    "structureScore": 11,
                    "expressionScore": 8
                  },
                  "summary": "分数越界",
                  "strengths": [],
                  "suggestions": []
                }
                """;

        assertThrows(AiStructuredOutputException.class,
                () -> parser.parseResumeScoreResult(response));
    }

    @Test
    void normalizesResumeScoreDetailOutsideAllowedRange() throws Exception {
        String response = """
                {
                  "overallScore": 82,
                  "scoreDetail": {
                    "projectScore": 45,
                    "skillMatchScore": 18,
                    "contentScore": 13,
                    "structureScore": 20,
                    "expressionScore": 12
                  },
                  "summary": "分项分数有轻微越界，但结构有效。",
                  "strengths": [],
                  "suggestions": []
                }
                """;

        ResumeScoreResult result = parser.parseResumeScoreResult(response);

        assertEquals(40, result.getScoreDetail().getProjectScore());
        assertEquals(15, result.getScoreDetail().getStructureScore());
        assertEquals(10, result.getScoreDetail().getExpressionScore());
    }

    @Test
    void normalizesInterviewQuestionWithUnsupportedType() throws Exception {
        String response = """
                {
                  "questions": [
                    {
                      "question": "请介绍你的项目。",
                      "type": "SYSTEM_DESIGN",
                      "category": "项目经历"
                    },
                    {
                      "question": "你如何做 SQL 调优？",
                      "type": "DATABASE",
                      "category": "数据库"
                    }
                  ]
                }
                """;

        InterviewQuestions result = parser.parseInterviewQuestions(response);

        assertEquals("PROJECT", result.getQuestions().get(0).getType());
        assertEquals("MYSQL", result.getQuestions().get(1).getType());
    }

    @Test
    void rejectsJobDescriptionMatchWithIllegalMatchLevel() {
        String response = """
                {
                  "overallScore": 78,
                  "matchLevel": "非常匹配",
                  "summary": "非法枚举",
                  "matchedSkills": [],
                  "missingSkills": [],
                  "interviewFocus": [],
                  "risks": [],
                  "learningSuggestions": []
                }
                """;

        assertThrows(AiStructuredOutputException.class,
                () -> parser.parseJobDescriptionMatchResult(response));
    }

    @Test
    void rejectsInterviewEvaluationWithWrongArrayItemType() {
        String response = """
                {
                  "sessionId": "s1",
                  "totalQuestions": 1,
                  "overallScore": 80,
                  "categoryScores": ["bad-item"],
                  "questionDetails": [],
                  "overallFeedback": "ok",
                  "strengths": [],
                  "improvements": [],
                  "referenceAnswers": []
                }
                """;

        assertThrows(AiStructuredOutputException.class,
                () -> parser.parseInterviewEvaluation(response));
    }
}
