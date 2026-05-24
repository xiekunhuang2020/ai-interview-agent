package com.xkh.ai.interview.service.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.xkh.ai.interview.dto.AgentChatRequestDTO;
import com.xkh.ai.interview.service.audit.AiModelCallAuditRecorder;
import com.xkh.ai.interview.service.audit.AgentConversationAuditRecorder;
import com.xkh.ai.interview.service.llm.AiModelCallService;
import com.xkh.ai.interview.service.llm.PromptContextBudgetService;
import com.xkh.ai.interview.service.llm.PromptVersionRegistry;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class InterviewAssistantAgentService {

    private static final String STREAM_AGENT_NAME = "interview_assistant_stream_agent";
    private static final String SUMMARY_AGENT_NAME = "interview_assistant_summary_agent";
    private static final String OPERATION_NAME = "interview-assistant-stream";
    private static final String SUMMARY_OPERATION_NAME = "interview-assistant-summary";
    private static final String INTERVIEW_ASSISTANT_INSTRUCTION = """
            你是一个 AI 求职顾问，负责围绕候选人简历进行分析、追问设计和面试辅导。
            当用户提供 resumeId 时，应优先调用工具获取真实简历画像、已生成问题或相似简历上下文。
            事实边界：
            - get_resume_profile 和 get_resume_interview_questions 返回的是当前候选人的真实数据。
            - search_similar_resumes 返回的结果必须看 sourceType：CURRENT_RESUME_FACT 代表当前候选人事实，SIMILAR_RESUME_REFERENCE 只能作为相似简历参考。
            - 不得把相似简历片段写成当前候选人的项目经历、技能证据或评价依据。
            不要编造简历内容；如果工具没有返回信息，应明确说明缺少数据。
            面向中文用户回答，尽量使用产品化表达，避免暴露内部工具名。
            回答应聚焦 Java 后端、Spring Boot、MySQL、Redis、系统设计和智能体工程化。
            """;
    private static final String SUMMARY_INSTRUCTION = """
            你是 AI 求职顾问的会话摘要器，只负责压缩历史对话。
            摘要必须忠于原文，不新增事实，不扩展用户经历。
            只保留用户目标、已确认事实、关键建议、待跟进事项和明显偏好。
            使用中文，控制在 300 字以内。
            """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ToolCallbackProvider resumeToolCallbackProvider;
    private final AgentConversationAuditRecorder conversationAuditRecorder;
    private final AiModelCallAuditRecorder modelCallAuditRecorder;
    private final AiModelCallService aiModelCallService;
    private final PromptContextBudgetService contextBudgetService;
    private final PromptVersionRegistry promptVersionRegistry;
    private final ConcurrentMap<String, String> conversationSummaries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> summarizedMessageCounts = new ConcurrentHashMap<>();
    private final boolean summaryEnabled;
    private final int summaryTriggerMessages;
    private final int summaryKeepRecentMessages;

    public InterviewAssistantAgentService(ChatClient.Builder chatClientBuilder,
                                          @Qualifier("resumeToolCallbackProvider") ToolCallbackProvider resumeToolCallbackProvider,
                                          AgentConversationAuditRecorder conversationAuditRecorder,
                                          AiModelCallAuditRecorder modelCallAuditRecorder,
                                          AiModelCallService aiModelCallService,
                                          PromptContextBudgetService contextBudgetService,
                                          PromptVersionRegistry promptVersionRegistry,
                                          @Value("${ai-interview.agent.memory.max-messages:10}") int maxMemoryMessages,
                                          @Value("${ai-interview.agent.summary.enabled:true}") boolean summaryEnabled,
                                          @Value("${ai-interview.agent.summary.trigger-messages:8}") int summaryTriggerMessages,
                                          @Value("${ai-interview.agent.summary.keep-recent-messages:4}") int summaryKeepRecentMessages) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(Math.max(2, maxMemoryMessages))
                .build();
        this.resumeToolCallbackProvider = resumeToolCallbackProvider;
        this.conversationAuditRecorder = conversationAuditRecorder;
        this.modelCallAuditRecorder = modelCallAuditRecorder;
        this.aiModelCallService = aiModelCallService;
        this.contextBudgetService = contextBudgetService;
        this.promptVersionRegistry = promptVersionRegistry;
        this.summaryEnabled = summaryEnabled;
        this.summaryTriggerMessages = Math.max(2, summaryTriggerMessages);
        this.summaryKeepRecentMessages = Math.max(0, summaryKeepRecentMessages);
    }

    /**
     * 创建 Spring 官方 Flux SSE 流，用于 AI 求职顾问逐段返回回答内容。
     */
    public Flux<ServerSentEvent<Map<String, Object>>> stream(AgentChatRequestDTO request) {
        if (request == null || StringUtils.isBlank(request.getMessage())) {
            return Flux.just(sse("error", Map.of("message", "请输入要咨询的问题。")));
        }

        String conversationId = StringUtils.defaultIfBlank(request.getConversationId(), UUID.randomUUID().toString());
        String turnId = UUID.randomUUID().toString();

        return streamAnswer(request.getMessage(), conversationId, turnId);
    }

    /**
     * 调用模型流式生成答案，并把增量内容、审计日志和会话记忆串起来。
     */
    private Flux<ServerSentEvent<Map<String, Object>>> streamAnswer(String message, String conversationId, String turnId) {
        return Flux.defer(() -> {
            conversationAuditRecorder.recordUserMessage(conversationId, turnId, STREAM_AGENT_NAME, message);
            long start = System.currentTimeMillis();
            StringBuilder answer = new StringBuilder();
            String promptVersion = promptVersionRegistry.versionOf(OPERATION_NAME);
            AtomicReference<AiModelCallAuditRecorder.ModelUsage> usageRef = new AtomicReference<>();
            String limitedMessage = contextBudgetService.limitAssistantUserMessage(message);
            List<Message> messages = buildStreamingMessages(conversationId, limitedMessage);
            PromptContextBudgetService.ContextUsage contextUsage = contextBudgetService.contextUsageOf(messages);
            Prompt prompt = new Prompt(messages,
                    DashScopeChatOptions.builder()
                            .temperature(0.4)
                            .build());

            Flux<ServerSentEvent<Map<String, Object>>> meta = Flux.just(sse("meta", Map.of(
                    "conversationId", conversationId,
                    "turnId", turnId,
                    "agentName", STREAM_AGENT_NAME
            )));
            Flux<ServerSentEvent<Map<String, Object>>> deltas = chatClient.prompt(prompt)
                    .toolCallbacks(resumeToolCallbackProvider)
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> rememberModelUsage(usageRef, response))
                    .map(this::contentOf)
                    .filter(StringUtils::isNotBlank)
                    .doOnNext(answer::append)
                    .map(chunk -> sse("delta", Map.of("content", chunk)));
            Mono<ServerSentEvent<Map<String, Object>>> done = Mono.fromSupplier(() -> {
                long latencyMs = System.currentTimeMillis() - start;
                String finalAnswer = answer.toString();
                rememberConversation(conversationId, new UserMessage(limitedMessage), new AssistantMessage(finalAnswer));
                boolean summaryCompressed = summarizeConversationIfNeeded(conversationId, turnId);
                conversationAuditRecorder.recordAssistantMessage(
                        conversationId, turnId, STREAM_AGENT_NAME, finalAnswer, true, latencyMs, null);
                modelCallAuditRecorder.record(OPERATION_NAME, promptVersion, true, 1, latencyMs,
                        (String) null, usageRef.get(), contextUsage);
                return sse("done", Map.of(
                        "latencyMs", latencyMs,
                        "summaryCompressed", summaryCompressed
                ));
            });

            return Flux.concat(meta, deltas, done)
                    .onErrorResume(e -> {
                        recordFailedAssistantMessage(conversationId, turnId, STREAM_AGENT_NAME, start, e);
                        modelCallAuditRecorder.record(OPERATION_NAME, promptVersion, false, 1,
                                System.currentTimeMillis() - start, e, usageRef.get(), contextUsage);
                        return Flux.just(sse("error", Map.of("message", "顾问回答失败：" + e.getMessage())));
                    });
        });
    }

    /**
     * 从 Spring AI 官方流式 ChatResponse 中提取文本增量。
     */
    private String contentOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 保留流式响应中最新的模型名称和 token usage，完成时写入模型调用审计。
     */
    private void rememberModelUsage(AtomicReference<AiModelCallAuditRecorder.ModelUsage> usageRef,
                                    ChatResponse response) {
        AiModelCallAuditRecorder.ModelUsage currentUsage = modelCallAuditRecorder.usageOf(response);
        if (currentUsage == null) {
            return;
        }
        usageRef.updateAndGet(previousUsage -> mergeUsage(previousUsage, currentUsage));
    }

    /**
     * 合并流式响应分片中的 usage，避免后续分片缺字段时覆盖已有模型名或 token。
     */
    private AiModelCallAuditRecorder.ModelUsage mergeUsage(AiModelCallAuditRecorder.ModelUsage previousUsage,
                                                           AiModelCallAuditRecorder.ModelUsage currentUsage) {
        if (previousUsage == null) {
            return currentUsage;
        }
        return new AiModelCallAuditRecorder.ModelUsage(
                StringUtils.defaultIfBlank(currentUsage.modelName(), previousUsage.modelName()),
                currentUsage.inputTokens() == null ? previousUsage.inputTokens() : currentUsage.inputTokens(),
                currentUsage.outputTokens() == null ? previousUsage.outputTokens() : currentUsage.outputTokens(),
                currentUsage.totalTokens() == null ? previousUsage.totalTokens() : currentUsage.totalTokens()
        );
    }

    /**
     * 构造本轮模型输入，包含系统指令、历史上下文和当前用户问题。
     */
    private List<Message> buildStreamingMessages(String conversationId, String message) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(INTERVIEW_ASSISTANT_INSTRUCTION));
        String summary = conversationSummaries.get(conversationId);
        if (StringUtils.isNotBlank(summary)) {
            messages.add(new SystemMessage("## 已压缩会话摘要\n" + summary));
        }
        messages.addAll(chatMemory.get(conversationId));
        messages.add(new UserMessage(message));
        return messages;
    }

    /**
     * 使用 Spring AI 官方 ChatMemory 保存最近几轮对话，避免手写会话窗口。
     */
    private void rememberConversation(String conversationId, Message userMessage, Message assistantMessage) {
        chatMemory.add(conversationId, List.of(userMessage, assistantMessage));
    }

    /**
     * 历史消息达到阈值后生成摘要，并只保留最近几条原文消息。
     */
    private boolean summarizeConversationIfNeeded(String conversationId, String turnId) {
        if (!summaryEnabled) {
            return false;
        }
        List<Message> history = chatMemory.get(conversationId);
        int currentMessageCount = history.size();
        int lastSummarizedCount = summarizedMessageCounts.getOrDefault(conversationId, 0);
        if (currentMessageCount < summaryTriggerMessages || currentMessageCount <= lastSummarizedCount) {
            return false;
        }

        long start = System.currentTimeMillis();
        try {
            String summary = generateConversationSummary(conversationId, history);
            if (StringUtils.isBlank(summary)) {
                return false;
            }
            conversationSummaries.put(conversationId, summary);
            summarizedMessageCounts.put(conversationId, Math.min(summaryKeepRecentMessages, currentMessageCount));
            keepRecentMessages(conversationId, history);
            conversationAuditRecorder.recordAssistantMessage(
                    conversationId,
                    turnId,
                    SUMMARY_AGENT_NAME,
                    summary,
                    true,
                    System.currentTimeMillis() - start,
                    null
            );
            return true;
        } catch (RuntimeException e) {
            conversationAuditRecorder.recordAssistantMessage(
                    conversationId,
                    turnId,
                    SUMMARY_AGENT_NAME,
                    null,
                    false,
                    System.currentTimeMillis() - start,
                    e.getMessage()
            );
            return false;
        }
    }

    /**
     * 调用大模型把历史对话压缩成摘要，调用审计由统一模型服务记录。
     */
    private String generateConversationSummary(String conversationId, List<Message> history) {
        List<Message> messages = List.of(
                new SystemMessage(SUMMARY_INSTRUCTION),
                new UserMessage(buildSummaryPrompt(conversationSummaries.get(conversationId), history))
        );
        return aiModelCallService.call(SUMMARY_OPERATION_NAME, messages, 0.2);
    }

    /**
     * 组装摘要模型输入，包含上一版摘要和本次需要压缩的最近历史。
     */
    private String buildSummaryPrompt(String previousSummary, List<Message> history) {
        return """
                请基于以下信息生成新的会话摘要。

                ## 上一版摘要
                %s

                ## 最近对话
                %s
                """.formatted(
                StringUtils.defaultIfBlank(previousSummary, "无"),
                formatHistory(history)
        );
    }

    /**
     * 将消息历史格式化为摘要模型可读的角色文本。
     */
    private String formatHistory(List<Message> history) {
        List<String> lines = new ArrayList<>();
        for (Message message : history) {
            if (message == null || StringUtils.isBlank(message.getText())) {
                continue;
            }
            lines.add(roleName(message) + "：" + message.getText());
        }
        return String.join("\n\n", lines);
    }

    /**
     * 摘要完成后清空旧窗口，只保留最近几条原文消息用于承接下一轮对话。
     */
    private void keepRecentMessages(String conversationId, List<Message> history) {
        chatMemory.clear(conversationId);
        if (summaryKeepRecentMessages <= 0 || history.isEmpty()) {
            return;
        }
        int fromIndex = Math.max(0, history.size() - summaryKeepRecentMessages);
        chatMemory.add(conversationId, history.subList(fromIndex, history.size()));
    }

    /**
     * 将 Spring AI Message 类型转换为摘要里更易读的中文角色名。
     */
    private String roleName(Message message) {
        if (message instanceof UserMessage) {
            return "用户";
        }
        if (message instanceof AssistantMessage) {
            return "顾问";
        }
        return "系统";
    }

    /**
     * 创建带事件名的 Spring SSE 对象，保持前端 EventSource 消费格式稳定。
     */
    private ServerSentEvent<Map<String, Object>> sse(String eventName, Map<String, Object> data) {
        return ServerSentEvent.<Map<String, Object>>builder()
                .event(eventName)
                .data(data)
                .build();
    }

    /**
     * 记录顾问回答失败的审计日志，便于运营看板排查异常。
     */
    private void recordFailedAssistantMessage(String conversationId,
                                              String turnId,
                                              String agentName,
                                              long start,
                                              Throwable e) {
        long latencyMs = System.currentTimeMillis() - start;
        conversationAuditRecorder.recordAssistantMessage(
                conversationId, turnId, agentName, null, false, latencyMs, e.getMessage());
    }
}

