package com.xkh.ai.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.service.dto.JobDescriptionMatchResult;
import com.xkh.ai.interview.service.dto.ResumeScoreResult;
import com.xkh.ai.interview.support.AiJsonResponseParser;
import com.xkh.ai.interview.support.AiStructuredOutputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiJsonResponseParserTests {

    private final AiJsonResponseParser parser = new AiJsonResponseParser(new ObjectMapper());

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
}
