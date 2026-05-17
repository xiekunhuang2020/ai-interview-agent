package com.xkh.ai.interview.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeConfigTests {

    @Test
    void buildsInterviewAssistantReactAgent() {
        AgentRuntimeConfig config = new AgentRuntimeConfig();
        ReactAgent agent = config.interviewAssistantReactAgent(new StubChatModel(), ToolCallbackProvider.from());

        assertEquals("interview_assistant_agent", agent.name());
    }

    @Test
    void instructionSeparatesCandidateFactsFromSimilarResumeContext() {
        String instruction = AgentRuntimeConfig.INTERVIEW_ASSISTANT_INSTRUCTION;

        assertTrue(instruction.contains("get_resume_profile 和 get_resume_interview_questions 返回的是当前候选人的真实数据"));
        assertTrue(instruction.contains("search_similar_resumes 返回的是相似简历参考片段"));
        assertTrue(instruction.contains("不得把相似简历片段写成当前候选人的项目经历、技能证据或评价依据"));
    }

    private static class StubChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        }
    }
}
