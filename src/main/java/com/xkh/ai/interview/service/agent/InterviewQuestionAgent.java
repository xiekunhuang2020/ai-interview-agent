package com.xkh.ai.interview.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xkh.ai.interview.dto.InterviewQuestions;
import com.xkh.ai.interview.service.llm.AiJsonResponseParser;
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

    private final AiModelCallService aiModelCallService;
    private final AiJsonResponseParser responseParser;

    @Value("classpath:/prompt/interview-question-system.st")
    private Resource systemPromptResource;

    public InterviewQuestionAgent(AiModelCallService aiModelCallService, AiJsonResponseParser responseParser) {
        this.aiModelCallService = aiModelCallService;
        this.responseParser = responseParser;
    }

    public InterviewQuestions generate(String resumeText) {
        logger.info("InterviewQuestionAgent starts, resumeTextLength={}", resumeText.length());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptResource));
        messages.add(new UserMessage("""
                请根据以下简历内容生成面试问题：

                ## 候选人简历
                %s
                """.formatted(resumeText)));

        String response = aiModelCallService.call("interview-question-generation", messages, 0.7);
        try {
            return responseParser.parseInterviewQuestions(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("面试问题解析失败", e);
        }
    }
}
