package com.xkh.ai.interview.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xkh.ai.interview.dto.ResumeScoreResultDTO;
import com.xkh.ai.interview.service.llm.AiJsonResponseParser;
import com.xkh.ai.interview.service.llm.AiModelCallService;
import com.xkh.ai.interview.service.llm.AiStructuredOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ResumeAnalysisAgent {

    private static final Logger logger = LoggerFactory.getLogger(ResumeAnalysisAgent.class);
    private static final String OPERATION_NAME = "resume-analysis";

    private final AiModelCallService aiModelCallService;
    private final AiJsonResponseParser responseParser;

    @Value("classpath:/prompt/resume-analysis-system.st")
    private Resource systemPromptResource;

    @Value("classpath:/prompt/resume-analysis-user.st")
    private Resource userPromptResource;

    public ResumeAnalysisAgent(AiModelCallService aiModelCallService, AiJsonResponseParser responseParser) {
        this.aiModelCallService = aiModelCallService;
        this.responseParser = responseParser;
    }

    public ResumeScoreResultDTO analyze(String resumeText) throws IOException {
        logger.info("ResumeAnalysisAgent starts, resumeTextLength={}", resumeText.length());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptResource));

        PromptTemplate promptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        messages.add(new UserMessage(promptTemplate.render(Map.of("resumeText", resumeText))));

        String response = aiModelCallService.call(OPERATION_NAME, messages, 0.7);
        try {
            return responseParser.parseResumeScoreResult(response);
        } catch (AiStructuredOutputException e) {
            aiModelCallService.recordStructuredOutputFailure(OPERATION_NAME, e);
            throw e;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("简历评分结果解析失败", e);
        }
    }
}

