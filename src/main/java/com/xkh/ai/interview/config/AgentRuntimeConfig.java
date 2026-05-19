package com.xkh.ai.interview.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentRuntimeConfig {

    /**
     * AI 求职顾问的系统指令，定义 Agent 的角色、工具使用边界和回答风格。
     */
    public static final String INTERVIEW_ASSISTANT_INSTRUCTION = """
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

    /**
     * 构建 AI 求职顾问 ReAct Agent，并把简历工具注册给 Agent 调用。
     */
    @Bean
    public ReactAgent interviewAssistantReactAgent(
            ChatModel chatModel,
            @Qualifier("resumeToolCallbackProvider") ToolCallbackProvider resumeToolCallbackProvider) {
        return ReactAgent.builder()
                .name("interview_assistant_agent")
                .description("面向简历分析和模拟面试场景的 ReAct Agent，可按需调用简历画像、面试问题和相似简历检索工具。")
                .instruction(INTERVIEW_ASSISTANT_INSTRUCTION)
                .model(chatModel)
                .toolCallbackProviders(resumeToolCallbackProvider)
                .enableLogging(true)
                .build();
    }
}
