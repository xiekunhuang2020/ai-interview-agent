package com.xkh.ai.interview.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xkh.ai.interview.service.dto.InterviewQuestions;
import com.xkh.ai.interview.service.dto.ResumeSearchResult;
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
public class RagInterviewQuestionAgent {

    private static final Logger logger = LoggerFactory.getLogger(RagInterviewQuestionAgent.class);
    private static final int MAX_CONTEXT_CHARS_PER_RESUME = 1200;

    private final DashScopeChatModel chatModel;
    private final AiJsonResponseParser responseParser;

    @Value("classpath:/prompt/rag-interview-question-system.st")
    private Resource systemPromptResource;

    public RagInterviewQuestionAgent(DashScopeChatModel chatModel, AiJsonResponseParser responseParser) {
        this.chatModel = chatModel;
        this.responseParser = responseParser;
    }

    public InterviewQuestions generate(String resumeText,
                                       String jobDescription,
                                       List<ResumeSearchResult> retrievedResumes) {
        logger.info("RagInterviewQuestionAgent starts, resumeTextLength={}, jdLength={}, contextCount={}",
                resumeText.length(), jobDescription.length(), retrievedResumes.size());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptResource));
        messages.add(new UserMessage("""
                请生成岗位定制化面试问题。

                ## 候选人简历
                %s

                ## 目标岗位 JD
                %s

                ## RAG 检索上下文
                %s
                """.formatted(resumeText, jobDescription, buildRetrievalContext(retrievedResumes))));

        String response = callModel(messages);
        try {
            return responseParser.parseInterviewQuestions(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("RAG 面试问题解析失败", e);
        }
    }

    private String buildRetrievalContext(List<ResumeSearchResult> retrievedResumes) {
        if (retrievedResumes == null || retrievedResumes.isEmpty()) {
            return "未检索到参考简历片段，请仅基于候选人简历和岗位 JD 生成问题。";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < retrievedResumes.size(); i++) {
            ResumeSearchResult result = retrievedResumes.get(i);
            context.append("参考片段 ").append(i + 1).append("：\n");
            context.append("resumeId: ").append(result.getResumeId()).append("\n");
            context.append(truncate(result.getResumeText())).append("\n\n");
        }
        return context.toString();
    }

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_CONTEXT_CHARS_PER_RESUME) {
            return text == null ? "" : text;
        }
        return text.substring(0, MAX_CONTEXT_CHARS_PER_RESUME) + "\n...[truncated]";
    }

    private String callModel(List<Message> messages) {
        Prompt prompt = new Prompt(messages, DashScopeChatOptions.builder()
                .temperature(0.7)
                .build());
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
