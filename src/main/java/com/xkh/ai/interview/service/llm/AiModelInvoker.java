package com.xkh.ai.interview.service.llm;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.xkh.ai.interview.service.audit.AiModelCallAuditRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiModelInvoker {

    private static final Logger logger = LoggerFactory.getLogger(AiModelInvoker.class);

    private final ChatClient chatClient;
    private final PromptVersionRegistry promptVersionRegistry;
    private final AiModelCallAuditRecorder auditRecorder;
    private final AiModelFallbackResponseFactory fallbackResponseFactory;
    private final boolean fallbackEnabled;

    public AiModelInvoker(ChatClient.Builder chatClientBuilder,
                          PromptVersionRegistry promptVersionRegistry,
                          AiModelCallAuditRecorder auditRecorder,
                          AiModelFallbackResponseFactory fallbackResponseFactory,
                          @Value("${ai-interview.model.fallback-enabled:true}") boolean fallbackEnabled) {
        this.chatClient = chatClientBuilder.build();
        this.promptVersionRegistry = promptVersionRegistry;
        this.auditRecorder = auditRecorder;
        this.fallbackResponseFactory = fallbackResponseFactory;
        this.fallbackEnabled = fallbackEnabled;
    }

    public String call(String operationName, List<Message> messages, double temperature) {
        return call(operationName, messages, temperature, List.of());
    }

    public String call(String operationName, List<Message> messages, double temperature, List<Advisor> advisors) {
        String promptVersion = promptVersionRegistry.versionOf(operationName);
        Prompt prompt = new Prompt(messages, DashScopeChatOptions.builder()
                .temperature(temperature)
                .build());

        long start = System.currentTimeMillis();
        try {
            String text = doCall(prompt, advisors);
            long latencyMs = System.currentTimeMillis() - start;
            logger.info("AI model call succeeded, operation={}, promptVersion={}, latencyMs={}",
                    operationName, promptVersion, latencyMs);
            auditRecorder.record(operationName, promptVersion, true, false, 1, latencyMs, null);
            return text;
        } catch (RuntimeException e) {
            long latencyMs = System.currentTimeMillis() - start;
            logger.warn("AI model call failed after Spring AI retry, operation={}, promptVersion={}, latencyMs={}, error={}",
                    operationName, promptVersion, latencyMs, e.getMessage());
            return handleFailure(operationName, promptVersion, latencyMs, e);
        }
    }

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

    private String handleFailure(String operationName, String promptVersion, long latencyMs, RuntimeException error) {
        if (fallbackEnabled) {
            var fallbackResponse = fallbackResponseFactory.fallbackFor(operationName);
            if (fallbackResponse.isPresent()) {
                logger.warn("AI model call degraded with fallback response, operation={}, promptVersion={}, latencyMs={}",
                        operationName, promptVersion, latencyMs);
                auditRecorder.record(operationName, promptVersion, false, true, 1, latencyMs, error.getMessage());
                return fallbackResponse.get();
            }
        }

        auditRecorder.record(operationName, promptVersion, false, false, 1, latencyMs, error.getMessage());
        throw new AiModelCallException("AI 模型调用失败，operation=" + operationName, error);
    }

}
