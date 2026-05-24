package com.xkh.ai.interview.service.agent;

import com.xkh.ai.interview.dto.InterviewEvaluationDTO;
import com.xkh.ai.interview.dto.InterviewQuestionsDTO;
import com.xkh.ai.interview.service.llm.AiModelCallService;
import com.xkh.ai.interview.service.llm.PromptContextBudgetService;
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
    private static final String OPERATION_NAME = "answer-evaluation";

    private final AiModelCallService aiModelCallService;
    private final PromptContextBudgetService contextBudgetService;

    @Value("classpath:/prompt/interview-evaluation-system.st")
    private Resource systemPromptResource;

    /**
     * 注入统一模型调用服务，回答评估结果由 Spring AI 官方 entity 转为 DTO。
     */
    public AnswerEvaluationAgent(AiModelCallService aiModelCallService,
                                 PromptContextBudgetService contextBudgetService) {
        this.aiModelCallService = aiModelCallService;
        this.contextBudgetService = contextBudgetService;
    }

    /**
     * 根据候选人简历、面试题和用户答案生成评估报告。
     */
    public InterviewEvaluationDTO evaluate(String resumeText, InterviewQuestionsDTO questions, Map<Integer, String> answers) {
        logger.info("AnswerEvaluationAgent starts, questionCount={}", questions.getQuestions().size());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptResource));
        messages.add(new UserMessage(buildUserPrompt(resumeText, questions, answers)));

        return aiModelCallService.callEntity(OPERATION_NAME, messages, 0.7, InterviewEvaluationDTO.class);
    }

    /**
     * 拼装回答评估 Prompt 的用户消息。
     */
    private String buildUserPrompt(String resumeText, InterviewQuestionsDTO questions, Map<Integer, String> answers) {
        StringBuilder qaText = new StringBuilder();
        for (int i = 0; i < questions.getQuestions().size(); i++) {
            InterviewQuestionsDTO.Question question = questions.getQuestions().get(i);
            String answer = StringUtils.isBlank(answers.get(i))
                    ? "未作答"
                    : contextBudgetService.limitAnswer(answers.get(i));
            qaText.append("问题 %d [%s]: %s\n".formatted(i + 1, question.getType(), question.getQuestion()));
            qaText.append("候选人回答：%s\n\n".formatted(answer));
        }

        return """
                请结合候选人简历背景，评估以下面试问答：

                ## 候选人简历
                %s

                ## 面试问答
                %s
                """.formatted(contextBudgetService.limitResumeText(resumeText), qaText);
    }

}

