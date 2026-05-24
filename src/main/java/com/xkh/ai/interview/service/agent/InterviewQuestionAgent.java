package com.xkh.ai.interview.service.agent;

import com.xkh.ai.interview.dto.InterviewQuestionsDTO;
import com.xkh.ai.interview.service.llm.AiModelCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InterviewQuestionAgent {

    private static final Logger logger = LoggerFactory.getLogger(InterviewQuestionAgent.class);
    private static final String OPERATION_NAME = "interview-question-generation";

    private final AiModelCallService aiModelCallService;

    @Value("classpath:/prompt/interview-question-system.st")
    private Resource systemPromptResource;

    /**
     * 注入统一模型调用服务，面试题结果由 Spring AI 官方 entity 转为 DTO。
     */
    public InterviewQuestionAgent(AiModelCallService aiModelCallService) {
        this.aiModelCallService = aiModelCallService;
    }

    /**
     * 基于当前候选人简历生成基础面试题。
     */
    public InterviewQuestionsDTO generate(String resumeText) {
        logger.info("InterviewQuestionAgent starts, resumeTextLength={}", resumeText.length());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptResource));
        messages.add(new UserMessage("""
                请根据以下简历内容生成面试问题：

                ## 候选人简历
                %s
                """.formatted(resumeText)));

        return aiModelCallService.callEntity(OPERATION_NAME, messages, 0.7, InterviewQuestionsDTO.class);
    }
}
