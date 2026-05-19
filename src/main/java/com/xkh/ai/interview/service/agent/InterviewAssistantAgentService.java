package com.xkh.ai.interview.service.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.xkh.ai.interview.dto.AgentChatRequest;
import com.xkh.ai.interview.service.audit.AgentConversationAuditRecorder;
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
    private static final String INTERVIEW_ASSISTANT_INSTRUCTION = """
            你是一个 AI 求职顾问，负责围绕候选人简历进行分析、追问设计和面试辅导。
            当用户提供 resumeId 时，应优先调用工具获取真实简历画像、已生成问题或相似简历上下文。
            事实边界：
            - get_resume_profile 和 get_resume_interview_questions 返回的是当前候选人的真实数据。
            - search_similar_resumes 返回的是相似简历参考片段，只能用于判断同类岗位的追问方向、技能深度和面试难度。
            - 不得把相似简历片段写成当前候选人的项目经历、技能证据或评价依据。
            不要编造简历内容；如果工具没有返回信息，应明确说明缺少数据。
            面向中文用户回答，尽量使用产品化表达，避免暴露内部工具名。
            回答应聚焦 Java 后端、Spring Boot、MySQL、Redis、系统设计和智能体工程化。
            """;

    private final ChatClient chatClient;
    private final ToolCallbackProvider resumeToolCallbackProvider;
    private final AgentConversationAuditRecorder conversationAuditRecorder;
    private final ConcurrentMap<String, Deque<Message>> conversationHistory = new ConcurrentHashMap<>();

    public InterviewAssistantAgentService(ChatClient.Builder chatClientBuilder,
                                          @Qualifier("resumeToolCallbackProvider") ToolCallbackProvider resumeToolCallbackProvider,
                                          AgentConversationAuditRecorder conversationAuditRecorder) {
        this.chatClient = chatClientBuilder.build();
        this.resumeToolCallbackProvider = resumeToolCallbackProvider;
        this.conversationAuditRecorder = conversationAuditRecorder;
    }

    /**
     * 创建 SSE 流式响应，用于 AI 求职顾问逐段返回回答内容。
     */
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

    /**
     * 调用模型流式生成答案，并把增量内容、审计日志和会话记忆串起来。
     */
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

    /**
     * 构造本轮模型输入，包含系统指令、历史上下文和当前用户问题。
     */
    private List<Message> buildStreamingMessages(String conversationId, String message) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(INTERVIEW_ASSISTANT_INSTRUCTION));
        Deque<Message> history = conversationHistory.get(conversationId);
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(new UserMessage(message));
        return messages;
    }

    /**
     * 保存最近几轮对话，避免长对话时把上下文无限塞给模型。
     */
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

    /**
     * 追加模型返回片段，并通过 SSE delta 事件推送给前端。
     */
    private void appendAndSendDelta(SseEmitter emitter, StringBuilder answer, String chunk) {
        if (StringUtils.isBlank(chunk)) {
            return;
        }
        answer.append(chunk);
        sendEvent(emitter, "delta", Map.of("content", chunk));
    }

    /**
     * 统一发送 SSE 事件，封装底层 IO 异常。
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException | IllegalStateException e) {
            throw new IllegalStateException("SSE 发送失败", e);
        }
    }

    /**
     * 记录顾问回答失败的审计日志，便于运营看板排查异常。
     */
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
