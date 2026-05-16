package com.xkh.ai.interview.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xkh.ai.interview.service.dto.JobDescriptionMatchResult;
import com.xkh.ai.interview.support.AiJsonResponseParser;
import com.xkh.ai.interview.support.AiModelInvoker;
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
public class JobDescriptionMatchAgent {

    private static final Logger logger = LoggerFactory.getLogger(JobDescriptionMatchAgent.class);

    private final AiModelInvoker aiModelInvoker;
    private final AiJsonResponseParser responseParser;

    @Value("classpath:/prompt/jd-match-system.st")
    private Resource systemPromptResource;

    public JobDescriptionMatchAgent(AiModelInvoker aiModelInvoker, AiJsonResponseParser responseParser) {
        this.aiModelInvoker = aiModelInvoker;
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

        String response = aiModelInvoker.call("jd-match", messages, 0.5);
        try {
            return responseParser.parseJobDescriptionMatchResult(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JD 匹配结果解析失败", e);
        }
    }
}
