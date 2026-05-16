package com.xkh.ai.interview.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Arrays;
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
}
