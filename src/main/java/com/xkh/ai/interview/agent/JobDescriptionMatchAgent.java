package com.xkh.ai.interview.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xkh.ai.interview.service.dto.JobDescriptionMatchResult;
import com.xkh.ai.interview.support.AiJsonResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JobDescriptionMatchAgent {

    private static final Logger logger = LoggerFactory.getLogger(JobDescriptionMatchAgent.class);

    private final DashScopeChatModel chatModel;
    private final AiJsonResponseParser responseParser;

    @Value("classpath:/prompt/jd-match-system.st")
    private Resource systemPromptResource;

    public JobDescriptionMatchAgent(DashScopeChatModel chatModel, AiJsonResponseParser responseParser) {
        this.chatModel = chatModel;
        this.responseParser = responseParser;
    }

    public JobDescriptionMatchResult match(String resumeText, String jobDescription) {
        logger.info("JobDescriptionMatchAgent starts, resumeTextLength={}, jdLength={}",
                resumeText.length(), jobDescription.length());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptResource));
        messages.add(new UserMessage("""
                请分析以下候选人简历与岗位 JD 的匹配度：

                ## 候选人简历
                %s

                ## 岗位 JD
                %s
                """.formatted(resumeText, jobDescription)));

        String response = callModel(messages);
        try {
            return responseParser.parseJobDescriptionMatchResult(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JD 匹配结果解析失败", e);
        }
    }

    private String callModel(List<Message> messages) {
        Prompt prompt = new Prompt(messages, DashScopeChatOptions.builder()
                .temperature(0.5)
                .build());
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
