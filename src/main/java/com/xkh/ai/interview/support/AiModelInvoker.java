package com.xkh.ai.interview.support;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AiModelInvoker {

    private static final Logger logger = LoggerFactory.getLogger(AiModelInvoker.class);

    private final DashScopeChatModel chatModel;
    private final ExecutorService executorService;
    private final PromptVersionRegistry promptVersionRegistry;
    private final AiModelCallAuditRecorder auditRecorder;
    private final AiModelFallbackResponseFactory fallbackResponseFactory;
    private final boolean fallbackEnabled;
    private final int maxAttempts;
    private final Duration timeout;
    private final Duration backoff;

    public AiModelInvoker(DashScopeChatModel chatModel,
                          PromptVersionRegistry promptVersionRegistry,
                          AiModelCallAuditRecorder auditRecorder,
                          AiModelFallbackResponseFactory fallbackResponseFactory,
                          @Value("${ai-interview.model.fallback-enabled:true}") boolean fallbackEnabled,
                          @Value("${ai-interview.model.max-attempts:3}") int maxAttempts,
                          @Value("${ai-interview.model.timeout-seconds:60}") long timeoutSeconds,
                          @Value("${ai-interview.model.backoff-millis:800}") long backoffMillis,
                          @Value("${ai-interview.model.executor-pool-size:4}") int executorPoolSize) {
        this.chatModel = chatModel;
        this.promptVersionRegistry = promptVersionRegistry;
        this.auditRecorder = auditRecorder;
        this.fallbackResponseFactory = fallbackResponseFactory;
        this.fallbackEnabled = fallbackEnabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.backoff = Duration.ofMillis(Math.max(0, backoffMillis));
        this.executorService = Executors.newFixedThreadPool(Math.max(1, executorPoolSize), new NamedThreadFactory());
    }

    public String call(String operationName, List<Message> messages, double temperature) {
        String promptVersion = promptVersionRegistry.versionOf(operationName);
        Prompt prompt = new Prompt(messages, DashScopeChatOptions.builder()
                .temperature(temperature)
                .build());

        long totalStart = System.currentTimeMillis();
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long start = System.currentTimeMillis();
            try {
                String text = callOnce(prompt);
                long totalCostMs = System.currentTimeMillis() - totalStart;
                logger.info("AI model call succeeded, operation={}, promptVersion={}, attempt={}, attemptCostMs={}, totalCostMs={}",
                        operationName, promptVersion, attempt, System.currentTimeMillis() - start, totalCostMs);
                auditRecorder.record(operationName, promptVersion, true, false, attempt, totalCostMs, null);
                return text;
            } catch (RuntimeException e) {
                lastError = e;
                logger.warn("AI model call failed, operation={}, promptVersion={}, attempt={}, maxAttempts={}, costMs={}, error={}",
                        operationName, promptVersion, attempt, maxAttempts, System.currentTimeMillis() - start, e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        sleepBeforeRetry(attempt);
                    } catch (RuntimeException retryError) {
                        long totalCostMs = System.currentTimeMillis() - totalStart;
                        auditRecorder.record(operationName, promptVersion, false, false, attempt, totalCostMs, retryError.getMessage());
                        throw retryError;
                    }
                }
            }
        }

        long totalCostMs = System.currentTimeMillis() - totalStart;
        String errorMessage = lastError == null ? "unknown error" : lastError.getMessage();
        if (fallbackEnabled) {
            var fallbackResponse = fallbackResponseFactory.fallbackFor(operationName);
            if (fallbackResponse.isPresent()) {
                logger.warn("AI model call degraded with fallback response, operation={}, promptVersion={}, attempts={}, totalCostMs={}",
                        operationName, promptVersion, maxAttempts, totalCostMs);
                auditRecorder.record(operationName, promptVersion, false, true, maxAttempts, totalCostMs, errorMessage);
                return fallbackResponse.get();
            }
        }

        auditRecorder.record(operationName, promptVersion, false, false, maxAttempts, totalCostMs, errorMessage);
        throw new AiModelCallException("AI 模型调用失败，operation=" + operationName, lastError);
    }

    private String callOnce(Prompt prompt) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                chatModel.call(prompt).getResult().getOutput().getText(), executorService);

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new AiModelCallException("AI 模型调用超时，timeout=" + timeout.toSeconds() + "s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiModelCallException("AI 模型调用被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new AiModelCallException("AI 模型调用异常：" + cause.getMessage(), cause);
        }
    }

    private void sleepBeforeRetry(int attempt) {
        long sleepMillis = backoff.toMillis() * attempt;
        if (sleepMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiModelCallException("AI 模型重试等待被中断", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("ai-model-invoker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
