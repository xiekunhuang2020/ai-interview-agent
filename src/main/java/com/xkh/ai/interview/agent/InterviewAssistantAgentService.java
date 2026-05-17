package com.xkh.ai.interview.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.xkh.ai.interview.config.AgentRuntimeConfig;
import com.xkh.ai.interview.service.dto.AgentChatRequest;
import com.xkh.ai.interview.service.dto.AgentChatResponse;
import com.xkh.ai.interview.support.AgentConversationAuditRecorder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class InterviewAssistantAgentService {

    private static final long SSE_TIMEOUT_MS = 0L;
    private static final int MAX_HISTORY_MESSAGES = 10;
    private static final String STREAM_AGENT_NAME = "interview_assistant_stream_agent";

    private final ReactAgent interviewAssistantReactAgent;
    private final ChatClient chatClient;
    private final ToolCallbackProvider resumeToolCallbackProvider;
    private final AgentConversationAuditRecorder conversationAuditRecorder;
    private final ConcurrentMap<String, Deque<Message>> conversationHistory = new ConcurrentHashMap<>();

    public InterviewAssistantAgentService(ReactAgent interviewAssistantReactAgent,
                                          ChatClient.Builder chatClientBuilder,
                                          @Qualifier("resumeToolCallbackProvider") ToolCallbackProvider resumeToolCallbackProvider,
                                          AgentConversationAuditRecorder conversationAuditRecorder) {
        this.interviewAssistantReactAgent = interviewAssistantReactAgent;
        this.chatClient = chatClientBuilder.build();
        this.resumeToolCallbackProvider = resumeToolCallbackProvider;
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

    public SseEmitter stream(AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        if (request == null || StringUtils.isBlank(request.getMessage())) {
            CompletableFuture.runAsync(() -> {
                sendEvent(emitter, "error", Map.of("message", "请输入要咨询的问题。"));
                emitter.complete();
            });
            return emitter;
        }

        String conversationId = StringUtils.defaultIfBlank(request.getConversationId(), UUID.randomUUID().toString());
        String turnId = UUID.randomUUID().toString();

        CompletableFuture.runAsync(() -> streamAnswer(request.getMessage(), conversationId, turnId, emitter));
        return emitter;
    }

    private void streamAnswer(String message, String conversationId, String turnId, SseEmitter emitter) {
        conversationAuditRecorder.recordUserMessage(conversationId, turnId, STREAM_AGENT_NAME, message);
        sendEvent(emitter, "meta", Map.of(
                "conversationId", conversationId,
                "turnId", turnId,
                "agentName", STREAM_AGENT_NAME
        ));

        long start = System.currentTimeMillis();
        StringBuilder answer = new StringBuilder();
        try {
            Prompt prompt = new Prompt(buildStreamingMessages(conversationId, message),
                    DashScopeChatOptions.builder()
                            .temperature(0.4)
                            .build());

            chatClient.prompt(prompt)
                    .toolCallbacks(resumeToolCallbackProvider)
                    .stream()
                    .content()
                    .doOnNext(chunk -> appendAndSendDelta(emitter, answer, chunk))
                    .blockLast();

            long latencyMs = System.currentTimeMillis() - start;
            String finalAnswer = answer.toString();
            rememberConversation(conversationId, new UserMessage(message), new AssistantMessage(finalAnswer));
            conversationAuditRecorder.recordAssistantMessage(
                    conversationId, turnId, STREAM_AGENT_NAME, finalAnswer, true, latencyMs, null);
            sendEvent(emitter, "done", Map.of("latencyMs", latencyMs));
            emitter.complete();
        } catch (RuntimeException e) {
            recordFailedAssistantMessage(conversationId, turnId, STREAM_AGENT_NAME, start, e);
            sendEvent(emitter, "error", Map.of("message", "顾问回答失败：" + e.getMessage()));
            emitter.complete();
        }
    }

    private List<Message> buildStreamingMessages(String conversationId, String message) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(AgentRuntimeConfig.INTERVIEW_ASSISTANT_INSTRUCTION));
        Deque<Message> history = conversationHistory.get(conversationId);
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(new UserMessage(message));
        return messages;
    }

    private void rememberConversation(String conversationId, Message userMessage, Message assistantMessage) {
        Deque<Message> history = conversationHistory.computeIfAbsent(conversationId, key -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(userMessage);
            history.addLast(assistantMessage);
            while (history.size() > MAX_HISTORY_MESSAGES) {
                history.removeFirst();
            }
        }
    }

    private void appendAndSendDelta(SseEmitter emitter, StringBuilder answer, String chunk) {
        if (StringUtils.isBlank(chunk)) {
            return;
        }
        answer.append(chunk);
        sendEvent(emitter, "delta", Map.of("content", chunk));
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException | IllegalStateException e) {
            throw new IllegalStateException("SSE 发送失败", e);
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
