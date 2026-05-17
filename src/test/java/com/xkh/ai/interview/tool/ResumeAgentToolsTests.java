package com.xkh.ai.interview.tool;

import com.xkh.ai.interview.service.dto.InterviewQuestions;
import com.xkh.ai.interview.service.dto.ResumeData;
import com.xkh.ai.interview.service.dto.ResumeScoreResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeAgentToolsTests {

    @Test
    void exposesAnnotatedMethodsAsSpringAiToolCallbacks() {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(new ResumeAgentTools(null, null))
                .build()
                .getToolCallbacks();

        Map<String, ToolCallback> tools = Arrays.stream(callbacks)
                .collect(Collectors.toMap(callback -> callback.getToolDefinition().name(), Function.identity()));

        assertEquals(3, tools.size());
        assertTrue(tools.containsKey("get_resume_profile"));
        assertTrue(tools.containsKey("get_resume_interview_questions"));
        assertTrue(tools.containsKey("search_similar_resumes"));
        assertTrue(tools.get("search_similar_resumes").getToolDefinition().inputSchema().contains("queryText"));
        assertTrue(tools.get("search_similar_resumes").getToolDefinition().inputSchema().contains("topK"));
    }

    @Test
    void returnsCappedQuestionSummaryInsteadOfFullInterviewQuestionsDto() {
        ResumeAgentTools tools = new ResumeAgentTools(new StubResumeRepositoryTool(resumeWithQuestions()), null);

        ResumeAgentTools.InterviewQuestionsToolResult result = tools.getResumeInterviewQuestions("resume-001");

        assertEquals("resume-001", result.resumeId());
        assertEquals(12, result.totalQuestionCount());
        assertEquals(10, result.questions().size());
        assertTrue(result.questions().get(0).question().endsWith("...[truncated]"));
    }

    @Test
    void returnsSearchSnippetsWithNormalizedTopK() {
        CapturingResumeVectorTool vectorTool = new CapturingResumeVectorTool();
        ResumeAgentTools tools = new ResumeAgentTools(new StubResumeRepositoryTool(null), vectorTool);

        List<ResumeAgentTools.SimilarResumeToolResult> results =
                tools.searchSimilarResumes("Java Redis JD", 50);

        assertEquals(10, vectorTool.lastTopK);
        assertEquals(1, results.size());
        assertEquals("resume-002", results.get(0).resumeId());
        assertTrue(results.get(0).snippet().endsWith("...[truncated]"));
    }

    @Test
    void returnsCappedProfileListsAndSnippet() {
        ResumeAgentTools tools = new ResumeAgentTools(new StubResumeRepositoryTool(resumeWithProfile()), null);

        ResumeAgentTools.ResumeProfileToolResult result = tools.getResumeProfile("resume-001");

        assertEquals(5, result.strengths().size());
        assertEquals(5, result.suggestions().size());
        assertTrue(result.resumeTextSnippet().endsWith("...[truncated]"));
    }

    private ResumeData resumeWithQuestions() {
        List<InterviewQuestions.Question> questions = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            questions.add(new InterviewQuestions.Question("Q" + i + " " + "x".repeat(600), "PROJECT", "项目"));
        }
        return ResumeData.builder()
                .resumeId("resume-001")
                .questions(InterviewQuestions.builder().questions(questions).build())
                .build();
    }

    private ResumeData resumeWithProfile() {
        List<String> strengths = List.of("s1", "s2", "s3", "s4", "s5", "s6");
        List<ResumeScoreResult.Suggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            suggestions.add(new ResumeScoreResult.Suggestion("项目", "高", "issue" + i, "recommendation" + i));
        }
        ResumeScoreResult scoreResult = ResumeScoreResult.builder()
                .overallScore(88)
                .summary("summary")
                .strengths(strengths)
                .suggestions(suggestions)
                .build();
        return ResumeData.builder()
                .resumeId("resume-001")
                .resumeText("r".repeat(900))
                .scoreResult(scoreResult)
                .build();
    }

    private static class StubResumeRepositoryTool extends ResumeRepositoryTool {

        private final ResumeData resumeData;

        StubResumeRepositoryTool(ResumeData resumeData) {
            super(null, null, null);
            this.resumeData = resumeData;
        }

        @Override
        public ResumeData findById(String resumeId) {
            return resumeData;
        }
    }

    private static class CapturingResumeVectorTool extends ResumeVectorTool {

        private int lastTopK;

        CapturingResumeVectorTool() {
            super(null);
        }

        @Override
        public List<Document> search(String queryText, int topK) {
            this.lastTopK = topK;
            return List.of(new Document("完整简历-" + "x".repeat(800),
                    Map.of("resumeId", "resume-002", "fileName", "demo.pdf")));
        }
    }
}
