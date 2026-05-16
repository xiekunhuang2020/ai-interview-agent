package com.xkh.ai.interview.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiModelExecutionConfig {

    @Bean(name = "aiModelExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor aiModelExecutor(
            @Value("${ai-interview.model.executor-pool-size:4}") int executorPoolSize) {
        int normalizedPoolSize = Math.max(1, executorPoolSize);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(normalizedPoolSize);
        executor.setMaxPoolSize(normalizedPoolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-model-invoker-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
