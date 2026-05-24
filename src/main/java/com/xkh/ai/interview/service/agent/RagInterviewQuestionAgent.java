package com.xkh.ai.interview.service.agent;

import com.xkh.ai.interview.dto.InterviewQuestionsDTO;
import com.xkh.ai.interview.service.llm.AiModelCallService;
import com.xkh.ai.interview.service.rag.ResumeRagAdvisorFactory;
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
public class RagInterviewQuestionAgent {

    private static final Logger logger = LoggerFactory.getLogger(RagInterviewQuestionAgent.class);
    private static final String OPERATION_NAME = "rag-interview-question-generation";

    private final AiModelCallService aiModelCallService;
    private final ResumeRagAdvisorFactory ragAdvisorFactory;

    @Value("classpath:/prompt/rag-interview-question-system.st")
    private Resource systemPromptResource;

    /**
     * 注入统一模型调用服务和 RAG Advisor 工厂。
     */
    public RagInterviewQuestionAgent(AiModelCallService aiModelCallService,
                                     ResumeRagAdvisorFactory ragAdvisorFactory) {
        this.aiModelCallService = aiModelCallService;
        this.ragAdvisorFactory = ragAdvisorFactory;
    }

    /**
     * 基于当前简历、目标 JD 和相似简历参考片段生成岗位定制面试题。
     */
    public InterviewQuestionsDTO generate(String resumeText,
                                       String jobDescription,
                                       String resumeId,
                                       int topK) {
        logger.info("RagInterviewQuestionAgent starts, resumeId={}, resumeTextLength={}, jdLength={}, topK={}",
                resumeId, resumeText.length(), jobDescription.length(), topK);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptResource));
        messages.add(new UserMessage("""
                请生成岗位定制化面试问题。

                ## 候选人简历
                %s

                ## 目标岗位 JD
                %s
                """.formatted(resumeText, jobDescription)));

        return aiModelCallService.callEntity(
                OPERATION_NAME,
                messages,
                0.7,
                List.of(ragAdvisorFactory.createAdvisor(resumeId, topK, jobDescription)),
                InterviewQuestionsDTO.class
        );
    }

}
