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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
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
    private static final String QUERY_REWRITE_AGENT_NAME = "interview_assistant_query_rewrite_agent";
    private static final String OPERATION_NAME = "interview-assistant-stream";
    private static final String SUMMARY_OPERATION_NAME = "interview-assistant-summary";
    private static final String QUERY_REWRITE_OPERATION_NAME = "interview-assistant-query-rewrite";
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
    private final QueryTransformer queryRewriteTransformer;
    private final EmbeddingModel embeddingModel;
    private final ToolCallbackProvider resumeToolCallbackProvider;
    private final AgentConversationAuditRecorder conversationAuditRecorder;
    private final AiModelCallAuditRecorder modelCallAuditRecorder;
    private final AiModelCallService aiModelCallService;
    private final PromptContextBudgetService contextBudgetService;
    private final PromptVersionRegistry promptVersionRegistry;
    private final ConcurrentMap<String, String> conversationSummaries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> summarizedMessageCounts = new ConcurrentHashMap<>();
    private final boolean summaryEnabled;
    private final boolean queryRewriteEnabled;
    private final double queryRewriteMinSimilarity;
    private final int summaryTriggerMessages;
    private final int summaryKeepRecentMessages;

    public InterviewAssistantAgentService(ChatClient.Builder chatClientBuilder,
                                          @Qualifier("resumeToolCallbackProvider") ToolCallbackProvider resumeToolCallbackProvider,
                                          AgentConversationAuditRecorder conversationAuditRecorder,
                                          AiModelCallAuditRecorder modelCallAuditRecorder,
                                          AiModelCallService aiModelCallService,
                                          PromptContextBudgetService contextBudgetService,
                                          PromptVersionRegistry promptVersionRegistry,
                                          EmbeddingModel embeddingModel,
                                          @Value("${ai-interview.agent.memory.max-messages:10}") int maxMemoryMessages,
                                          @Value("${ai-interview.agent.summary.enabled:true}") boolean summaryEnabled,
                                          @Value("${ai-interview.agent.query-rewrite.enabled:true}") boolean queryRewriteEnabled,
                                          @Value("${ai-interview.agent.query-rewrite.min-similarity:0.75}") double queryRewriteMinSimilarity,
                                          @Value("${ai-interview.agent.summary.trigger-messages:8}") int summaryTriggerMessages,
                                          @Value("${ai-interview.agent.summary.keep-recent-messages:4}") int summaryKeepRecentMessages) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(Math.max(2, maxMemoryMessages))
                .build();
        this.queryRewriteTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .targetSearchSystem("AI 求职顾问工具和简历知识库")
                .build();
        this.resumeToolCallbackProvider = resumeToolCallbackProvider;
        this.conversationAuditRecorder = conversationAuditRecorder;
        this.modelCallAuditRecorder = modelCallAuditRecorder;
        this.aiModelCallService = aiModelCallService;
        this.contextBudgetService = contextBudgetService;
        this.promptVersionRegistry = promptVersionRegistry;
        this.embeddingModel = embeddingModel;
        this.summaryEnabled = summaryEnabled;
        this.queryRewriteEnabled = queryRewriteEnabled;
        this.queryRewriteMinSimilarity = normalizeSimilarityThreshold(queryRewriteMinSimilarity);
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
            QueryRewriteResult queryRewriteResult = rewriteQuery(conversationId, turnId, limitedMessage);
            List<Message> messages = buildStreamingMessages(conversationId, limitedMessage, queryRewriteResult.rewrittenQuery());
            PromptContextBudgetService.ContextUsage contextUsage = contextBudgetService.contextUsageOf(messages);
            Prompt prompt = new Prompt(messages,
                    DashScopeChatOptions.builder()
                            .temperature(0.4)
                            .build());

            Flux<ServerSentEvent<Map<String, Object>>> meta = Flux.just(sse("meta", Map.of(
                    "conversationId", conversationId,
                    "turnId", turnId,
                    "agentName", STREAM_AGENT_NAME,
                    "summaryCompressed", hasConversationSummary(conversationId)
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
                modelCallAuditRecorder.record(OPERATION_NAME, promptVersion, true, latencyMs,
                        (String) null, usageRef.get(), contextUsage);
                return sse("done", Map.of(
                        "latencyMs", latencyMs,
                        "summaryCompressed", summaryCompressed
                ));
            });

            return Flux.concat(meta, deltas, done)
                    .onErrorResume(e -> {
                        recordFailedAssistantMessage(conversationId, turnId, STREAM_AGENT_NAME, start, e);
                        modelCallAuditRecorder.record(OPERATION_NAME, promptVersion, false,
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
    private List<Message> buildStreamingMessages(String conversationId, String message, String rewrittenQuery) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(INTERVIEW_ASSISTANT_INSTRUCTION));
        String summary = conversationSummaries.get(conversationId);
        if (StringUtils.isNotBlank(summary)) {
            messages.add(new SystemMessage("## 已压缩会话摘要\n" + summary));
        }
        messages.addAll(chatMemory.get(conversationId));
        if (shouldInjectRewrittenQuery(message, rewrittenQuery)) {
            messages.add(new SystemMessage("""
                    ## 本轮检索意图
                    以下内容由 Query Rewrite 生成，只用于辅助工具调用和检索，不替代用户原始问题。
                    %s
                    """.formatted(rewrittenQuery)));
        }
        messages.add(new UserMessage(message));
        return messages;
    }

    /**
     * 使用 Spring AI 官方 RewriteQueryTransformer 把口语化问题改写成更稳定的检索意图。
     */
    private QueryRewriteResult rewriteQuery(String conversationId, String turnId, String message) {
        if (!queryRewriteEnabled || StringUtils.isBlank(message)) {
            return new QueryRewriteResult(message);
        }

        long start = System.currentTimeMillis();
        String promptVersion = promptVersionRegistry.versionOf(QUERY_REWRITE_OPERATION_NAME);
        List<Message> history = chatMemory.get(conversationId);
        PromptContextBudgetService.ContextUsage contextUsage = contextBudgetService.contextUsageOf(rewriteContextMessages(history, message));
        try {
            Query rewritten = queryRewriteTransformer.transform(new Query(message, history, Map.of()));
            String rewrittenQuery = normalizeRewrittenQuery(message, rewritten);
            QueryRewriteValidation validation = validateRewrittenQuery(message, rewrittenQuery);
            String effectiveQuery = validation.accepted() ? rewrittenQuery : message;
            long latencyMs = System.currentTimeMillis() - start;
            conversationAuditRecorder.recordSystemMessage(
                    conversationId,
                    turnId,
                    QUERY_REWRITE_AGENT_NAME,
                    formatQueryRewriteMessage(message, rewrittenQuery, validation),
                    true,
                    latencyMs,
                    null
            );
            modelCallAuditRecorder.record(QUERY_REWRITE_OPERATION_NAME, promptVersion, true,
                    latencyMs, (String) null, null, contextUsage);
            return new QueryRewriteResult(effectiveQuery);
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - start;
            conversationAuditRecorder.recordSystemMessage(
                    conversationId,
                    turnId,
                    QUERY_REWRITE_AGENT_NAME,
                    formatQueryRewriteFailureMessage(message),
                    false,
                    latencyMs,
                    e.getMessage()
            );
            modelCallAuditRecorder.record(QUERY_REWRITE_OPERATION_NAME, promptVersion, false,
                    latencyMs, e, null, contextUsage);
            return new QueryRewriteResult(message);
        }
    }

    /**
     * 组装 Query Rewrite 的上下文统计样本，用于看板观察本次改写输入规模。
     */
    private List<Message> rewriteContextMessages(List<Message> history, String message) {
        List<Message> messages = new ArrayList<>();
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(new UserMessage(message));
        return messages;
    }

    /**
     * 清理官方改写结果，空结果或无效结果直接回退到原始问题。
     */
    private String normalizeRewrittenQuery(String originalMessage, Query rewritten) {
        String rewrittenText = rewritten == null ? null : rewritten.text();
        rewrittenText = StringUtils.trimToEmpty(rewrittenText)
                .replaceAll("^```[a-zA-Z]*", "")
                .replaceAll("```$", "")
                .trim();
        return StringUtils.defaultIfBlank(rewrittenText, originalMessage);
    }

    /**
     * 对 Query Rewrite 结果做语义一致性校验。
     *
     * 这是改写链路的安全阀：改写只能让检索表达更稳定，不能改变用户真实意图。
     * 这里复用 Spring AI 官方 EmbeddingModel 计算原问题和改写问题的余弦相似度；
     * 低于阈值时丢弃改写，后续仍使用原始问题。
     */
    private QueryRewriteValidation validateRewrittenQuery(String originalMessage, String rewrittenQuery) {
        if (!shouldInjectRewrittenQuery(originalMessage, rewrittenQuery)) {
            return QueryRewriteValidation.unchanged();
        }
        try {
            double similarity = cosineSimilarity(
                    embeddingModel.embed(originalMessage),
                    embeddingModel.embed(rewrittenQuery)
            );
            boolean accepted = similarity >= queryRewriteMinSimilarity;
            return new QueryRewriteValidation(accepted, similarity, null);
        } catch (RuntimeException ex) {
            return new QueryRewriteValidation(false, null, "语义校验失败：" + ex.getMessage());
        }
    }

    /**
     * 计算两个 embedding 向量的余弦相似度，用于判断改写前后是否仍在表达同一意图。
     */
    private double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0.0D;
        }
        int length = Math.min(left.length, right.length);
        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int i = 0; i < length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0D || rightNorm == 0.0D) {
            return 0.0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * 规范 Query Rewrite 相似度阈值，避免配置错误导致所有改写都被接受或丢弃。
     */
    private double normalizeSimilarityThreshold(double threshold) {
        if (Double.isNaN(threshold)) {
            return 0.75D;
        }
        return Math.max(0.0D, Math.min(1.0D, threshold));
    }

    /**
     * 判断是否需要把改写后的检索意图注入 Prompt，避免无变化时增加噪声。
     */
    private boolean shouldInjectRewrittenQuery(String originalMessage, String rewrittenQuery) {
        return StringUtils.isNotBlank(rewrittenQuery)
                && !StringUtils.equals(StringUtils.trimToEmpty(originalMessage), StringUtils.trimToEmpty(rewrittenQuery));
    }

    /**
     * 将原始问题和改写问题写成看板可读格式，方便排查 Query Rewrite 是否跑偏。
     */
    private String formatQueryRewriteMessage(String originalMessage,
                                             String rewrittenQuery,
                                             QueryRewriteValidation validation) {
        return """
                原始问题：%s
                改写后：%s
                语义校验：%s
                """.formatted(originalMessage, rewrittenQuery, validation.toAuditText(queryRewriteMinSimilarity)).trim();
    }

    /**
     * Query Rewrite 失败时保留原始问题，方便从面板定位降级原因。
     */
    private String formatQueryRewriteFailureMessage(String originalMessage) {
        return "Query Rewrite 失败，已使用原始问题继续：%s".formatted(originalMessage);
    }

    /**
     * 判断当前会话是否已经有压缩摘要，用于前端展示会话状态。
     */
    private boolean hasConversationSummary(String conversationId) {
        return StringUtils.isNotBlank(conversationSummaries.get(conversationId));
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

    private record QueryRewriteResult(String rewrittenQuery) {
    }

    private record QueryRewriteValidation(boolean accepted, Double similarity, String reason) {

        private static QueryRewriteValidation unchanged() {
            return new QueryRewriteValidation(true, null, "改写无变化");
        }

        private String toAuditText(double threshold) {
            if (similarity == null) {
                return accepted ? reason : reason + "，已使用原始问题";
            }
            String status = accepted ? "通过，采用改写" : "未通过，已丢弃改写";
            return "%s，相似度 %.3f，阈值 %.3f".formatted(status, similarity, threshold);
        }
    }
}

