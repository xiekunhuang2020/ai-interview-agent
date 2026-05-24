package com.xkh.ai.interview.service.agent;

import com.xkh.ai.interview.dto.JobDescriptionMatchResultDTO;
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
public class JobDescriptionMatchAgent {

    private static final Logger logger = LoggerFactory.getLogger(JobDescriptionMatchAgent.class);
    private static final String OPERATION_NAME = "jd-match";

    private final AiModelCallService aiModelCallService;

    @Value("classpath:/prompt/jd-match-system.st")
    private Resource systemPromptResource;

    /**
     * 注入统一模型调用服务，岗位匹配结果由 Spring AI 官方 entity 转为 DTO。
     */
    public JobDescriptionMatchAgent(AiModelCallService aiModelCallService) {
        this.aiModelCallService = aiModelCallService;
    }

    /**
     * 根据候选人简历和目标 JD 生成岗位匹配分析。
     */
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

        return aiModelCallService.callEntity(OPERATION_NAME, messages, 0.5, JobDescriptionMatchResultDTO.class);
    }
}
