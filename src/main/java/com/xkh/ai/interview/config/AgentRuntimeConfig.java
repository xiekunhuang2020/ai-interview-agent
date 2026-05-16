package com.xkh.ai.interview.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentRuntimeConfig {

    @Bean
    public ReactAgent interviewAssistantReactAgent(
            ChatModel chatModel,
            @Qualifier("resumeToolCallbackProvider") ToolCallbackProvider resumeToolCallbackProvider) {
        return ReactAgent.builder()
                .name("interview_assistant_agent")
                .description("面向简历分析和模拟面试场景的 ReAct Agent，可按需调用简历画像、面试问题和相似简历检索工具。")
                .instruction("""
                        你是一个 AI Agent 面试助手，负责围绕候选人简历进行分析、追问设计和面试辅导。
                        当用户提供 resumeId 时，应优先调用工具获取真实简历画像、已生成问题或相似简历上下文。
                        不要编造简历内容；如果工具没有返回信息，应明确说明缺少数据。
                        回答应聚焦 Java 后端、Spring Boot、MySQL、Redis、系统设计和 AI Agent 工程化。
                        """)
                .model(chatModel)
                .toolCallbackProviders(resumeToolCallbackProvider)
                .enableLogging(true)
                .build();
    }
}
