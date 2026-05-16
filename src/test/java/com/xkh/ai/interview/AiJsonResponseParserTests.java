package com.xkh.ai.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.service.dto.ResumeScoreResult;
import com.xkh.ai.interview.support.AiJsonResponseParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
