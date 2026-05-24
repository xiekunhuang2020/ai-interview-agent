package com.xkh.ai.interview.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xkh.ai.interview.dto.JobDescriptionMatchResultDTO;
import com.xkh.ai.interview.service.llm.AiJsonResponseParser;
import com.xkh.ai.interview.service.llm.AiModelCallService;
import com.xkh.ai.interview.service.llm.AiStructuredOutputException;
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
    private static final String OPERATION_NAME = "jd-match";

    private final AiModelCallService aiModelCallService;
    private final AiJsonResponseParser responseParser;

    @Value("classpath:/prompt/jd-match-system.st")
    private Resource systemPromptResource;

    public JobDescriptionMatchAgent(AiModelCallService aiModelCallService, AiJsonResponseParser responseParser) {
        this.aiModelCallService = aiModelCallService;
        this.responseParser = responseParser;
    }

    public JobDescriptionMatchResultDTO match(String resumeText, String jobDescription) {
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

        String response = aiModelCallService.call(OPERATION_NAME, messages, 0.5);
        try {
            return responseParser.parseJobDescriptionMatchResult(response);
        } catch (AiStructuredOutputException e) {
            aiModelCallService.recordStructuredOutputFailure(OPERATION_NAME, e);
            throw e;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JD 匹配结果解析失败", e);
        }
    }
}

