package com.xkh.ai.interview.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xkh.ai.interview.dto.InterviewEvaluationDTO;
import com.xkh.ai.interview.dto.InterviewQuestionsDTO;
import com.xkh.ai.interview.service.llm.AiJsonResponseParser;
import com.xkh.ai.interview.service.llm.AiModelCallService;
import org.apache.commons.lang3.StringUtils;
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
import java.util.Map;

@Component
public class AnswerEvaluationAgent {

    private static final Logger logger = LoggerFactory.getLogger(AnswerEvaluationAgent.class);

    private final AiModelCallService aiModelCallService;
    private final AiJsonResponseParser responseParser;

    @Value("classpath:/prompt/interview-evaluation-system.st")
    private Resource systemPromptResource;

    public AnswerEvaluationAgent(AiModelCallService aiModelCallService, AiJsonResponseParser responseParser) {
        this.aiModelCallService = aiModelCallService;
        this.responseParser = responseParser;
    }

    public InterviewEvaluationDTO evaluate(String resumeText, InterviewQuestionsDTO questions, Map<Integer, String> answers) {
        logger.info("AnswerEvaluationAgent starts, questionCount={}", questions.getQuestions().size());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptResource));
        messages.add(new UserMessage(buildUserPrompt(resumeText, questions, answers)));

        String response = aiModelCallService.call("answer-evaluation", messages, 0.7);
        try {
            return responseParser.parseInterviewEvaluation(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("面试评估结果解析失败", e);
        }
    }

    private String buildUserPrompt(String resumeText, InterviewQuestionsDTO questions, Map<Integer, String> answers) {
        StringBuilder qaText = new StringBuilder();
        for (int i = 0; i < questions.getQuestions().size(); i++) {
            InterviewQuestionsDTO.Question question = questions.getQuestions().get(i);
            String answer = StringUtils.isBlank(answers.get(i)) ? "未作答" : answers.get(i);
            qaText.append("问题 %d [%s]: %s\n".formatted(i + 1, question.getType(), question.getQuestion()));
            qaText.append("候选人回答：%s\n\n".formatted(answer));
        }

        return """
                请结合候选人简历背景，评估以下面试问答：

                ## 候选人简历
                %s

                ## 面试问答
                %s
                """.formatted(resumeText, qaText);
    }

}

