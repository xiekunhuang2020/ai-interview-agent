package com.xkh.ai.interview.service.llm;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.xkh.ai.interview.service.audit.AiModelCallAuditRecorder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiModelCallService {

    private static final Logger logger = LoggerFactory.getLogger(AiModelCallService.class);

    private final ChatClient chatClient;
    private final PromptVersionRegistry promptVersionRegistry;
    private final AiModelCallAuditRecorder auditRecorder;

    /**
     * 使用 Spring AI 提供的 ChatClient 作为模型调用入口。
     * 本类只补充业务侧需要的 Prompt 版本、调用审计和异常映射，不替代框架重试、流式和工具调用能力。
     */
    public AiModelCallService(ChatClient.Builder chatClientBuilder,
                              PromptVersionRegistry promptVersionRegistry,
                              AiModelCallAuditRecorder auditRecorder) {
        this.chatClient = chatClientBuilder.build();
        this.promptVersionRegistry = promptVersionRegistry;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 调用大模型并记录审计，适用于不需要 RAG Advisor 的普通模型任务。
     */
    public String call(String operationName, List<Message> messages, double temperature) {
        return call(operationName, messages, temperature, List.of());
    }

    /**
     * 调用大模型并记录审计，advisors 由 Spring AI 官方 Advisor 机制处理。
     */
    public String call(String operationName, List<Message> messages, double temperature, List<Advisor> advisors) {
        String promptVersion = promptVersionRegistry.versionOf(operationName);
        Prompt prompt = new Prompt(messages, DashScopeChatOptions.builder()
                .temperature(temperature)
                .build());

        long start = System.currentTimeMillis();
        try {
            String text = doCall(prompt, advisors);
            if (StringUtils.isBlank(text)) {
                throw new IllegalStateException("AI 模型返回空内容");
            }
            long latencyMs = System.currentTimeMillis() - start;
            logger.info("AI model call succeeded, operation={}, promptVersion={}, latencyMs={}",
                    operationName, promptVersion, latencyMs);
            auditRecorder.record(operationName, promptVersion, true, 1, latencyMs, (String) null);
            return text;
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - start;
            logger.warn("AI model call failed after Spring AI retry, operation={}, promptVersion={}, latencyMs={}, error={}",
                    operationName, promptVersion, latencyMs, e.getMessage());
            auditRecorder.record(operationName, promptVersion, false, 1, latencyMs, e);
            throw new AiModelCallException("AI 模型调用失败，operation=" + operationName, e);
        }
    }

    /**
     * 按是否存在 Advisor 选择 Spring AI 调用链，避免业务层自己处理 RAG 注入细节。
     */
    private String doCall(Prompt prompt, List<Advisor> advisors) {
        if (advisors == null || advisors.isEmpty()) {
            return chatClient.prompt(prompt)
                    .call()
                    .content();
        }
        return chatClient.prompt(prompt)
                .advisors(advisors)
                .call()
                .content();
    }

}
