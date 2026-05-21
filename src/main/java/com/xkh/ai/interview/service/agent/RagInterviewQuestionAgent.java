package com.xkh.ai.interview.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xkh.ai.interview.dto.InterviewQuestionsDTO;
import com.xkh.ai.interview.service.llm.AiJsonResponseParser;
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

    private final AiModelCallService aiModelCallService;
    private final AiJsonResponseParser responseParser;
    private final ResumeRagAdvisorFactory ragAdvisorFactory;

    @Value("classpath:/prompt/rag-interview-question-system.st")
    private Resource systemPromptResource;

    public RagInterviewQuestionAgent(AiModelCallService aiModelCallService,
                                     AiJsonResponseParser responseParser,
                                     ResumeRagAdvisorFactory ragAdvisorFactory) {
        this.aiModelCallService = aiModelCallService;
        this.responseParser = responseParser;
        this.ragAdvisorFactory = ragAdvisorFactory;
    }

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

        String response = aiModelCallService.call(
                "rag-interview-question-generation",
                messages,
                0.7,
                List.of(ragAdvisorFactory.createAdvisor(resumeId, topK, jobDescription))
        );
        try {
            return responseParser.parseInterviewQuestions(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("RAG 面试问题解析失败", e);
        }
    }

}

