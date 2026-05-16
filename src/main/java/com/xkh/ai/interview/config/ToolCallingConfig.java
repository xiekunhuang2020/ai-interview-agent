package com.xkh.ai.interview.config;

import com.xkh.ai.interview.tool.ResumeAgentTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolCallingConfig {

    @Bean
    public ToolCallbackProvider resumeToolCallbackProvider(ResumeAgentTools resumeAgentTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(resumeAgentTools)
                .build();
    }
}
