package com.xkh.ai.interview.config;

import com.xkh.ai.interview.service.tool.ResumeAgentTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolCallingConfig {

    /**
     * 把 ResumeAgentTools 中带 @Tool 的方法暴露成 Spring AI 可调用工具。
     */
    @Bean
    public ToolCallbackProvider resumeToolCallbackProvider(ResumeAgentTools resumeAgentTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(resumeAgentTools)
                .build();
    }
}
