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

class AgentRuntimeConfigTests {

    @Test
    void buildsInterviewAssistantReactAgent() {
        AgentRuntimeConfig config = new AgentRuntimeConfig();
        ReactAgent agent = config.interviewAssistantReactAgent(new StubChatModel(), ToolCallbackProvider.from());

        assertEquals("interview_assistant_agent", agent.name());
    }

    private static class StubChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        }
    }
}
