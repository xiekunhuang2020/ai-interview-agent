package com.xkh.ai.interview.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.xkh.ai.interview.service.dto.AgentChatRequest;
import com.xkh.ai.interview.service.dto.AgentChatResponse;
import com.xkh.ai.interview.support.AgentConversationAuditRecorder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class InterviewAssistantAgentService {

    private final ReactAgent interviewAssistantReactAgent;
    private final AgentConversationAuditRecorder conversationAuditRecorder;

    public InterviewAssistantAgentService(ReactAgent interviewAssistantReactAgent,
                                          AgentConversationAuditRecorder conversationAuditRecorder) {
        this.interviewAssistantReactAgent = interviewAssistantReactAgent;
        this.conversationAuditRecorder = conversationAuditRecorder;
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        if (request == null || StringUtils.isBlank(request.getMessage())) {
            throw new IllegalArgumentException("message 不能为空");
        }

        String conversationId = StringUtils.defaultIfBlank(request.getConversationId(), UUID.randomUUID().toString());
        String turnId = UUID.randomUUID().toString();
        String agentName = interviewAssistantReactAgent.name();
        RunnableConfig config = RunnableConfig.builder()
                .threadId(conversationId)
                .build();

        conversationAuditRecorder.recordUserMessage(conversationId, turnId, agentName, request.getMessage());
        long start = System.currentTimeMillis();
        try {
            AssistantMessage assistantMessage = interviewAssistantReactAgent.call(request.getMessage(), config);
            long latencyMs = System.currentTimeMillis() - start;
            String answer = assistantMessage == null ? "" : assistantMessage.getText();
            conversationAuditRecorder.recordAssistantMessage(
                    conversationId, turnId, agentName, answer, true, latencyMs, null);
            return new AgentChatResponse(
                    conversationId,
                    turnId,
                    agentName,
                    answer,
                    latencyMs
            );
        } catch (GraphRunnerException e) {
            recordFailedAssistantMessage(conversationId, turnId, agentName, start, e);
            throw new IllegalStateException("Agent 执行失败：" + e.getMessage(), e);
        } catch (RuntimeException e) {
            recordFailedAssistantMessage(conversationId, turnId, agentName, start, e);
            throw e;
        }
    }

    private void recordFailedAssistantMessage(String conversationId,
                                              String turnId,
                                              String agentName,
                                              long start,
                                              Exception e) {
        long latencyMs = System.currentTimeMillis() - start;
        conversationAuditRecorder.recordAssistantMessage(
                conversationId, turnId, agentName, null, false, latencyMs, e.getMessage());
    }
}
