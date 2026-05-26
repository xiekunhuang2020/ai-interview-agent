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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public InterviewEvaluationDTO evaluate(String resumeText,
                                           InterviewQuestionsDTO questions,
                                           Map<Integer, String> answers,
                                           List<Integer> voiceAnswerIndexes) {
        logger.info("AnswerEvaluationAgent starts, questionCount={}", questions.getQuestions().size());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptResource));
        messages.add(new UserMessage(buildUserPrompt(resumeText, questions, answers, voiceAnswerIndexes)));

        InterviewEvaluationDTO evaluation = aiModelCallService.callEntity(OPERATION_NAME, messages, 0.2,
                InterviewEvaluationDTO.class);
        return fillEvaluationDefaults(evaluation, voiceAnswerIndexes);
    }

    /**
     * 拼装回答评估 Prompt 的用户消息。
     */
    private String buildUserPrompt(String resumeText,
                                   InterviewQuestionsDTO questions,
                                   Map<Integer, String> answers,
                                   List<Integer> voiceAnswerIndexes) {
        Map<Integer, String> safeAnswers = answers == null ? Map.of() : answers;
        Set<Integer> voiceIndexes = voiceAnswerIndexes == null ? Set.of() : new HashSet<>(voiceAnswerIndexes);
        StringBuilder qaText = new StringBuilder();
        for (int i = 0; i < questions.getQuestions().size(); i++) {
            InterviewQuestionsDTO.Question question = questions.getQuestions().get(i);
            String answer = StringUtils.isBlank(safeAnswers.get(i))
                    ? "未作答"
                    : contextBudgetService.limitAnswer(safeAnswers.get(i));
            String answerMode = voiceIndexes.contains(i) ? "VOICE_TRANSCRIPT" : "TEXT";
            qaText.append("问题 %d [%s]: %s\n".formatted(i + 1, question.getType(), question.getQuestion()));
            qaText.append("作答来源：%s\n".formatted(answerMode));
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

    /**
     * 补齐模型可能遗漏的非核心字段，避免新增语音建议字段影响已有回答评估链路。
     */
    private InterviewEvaluationDTO fillEvaluationDefaults(InterviewEvaluationDTO evaluation,
                                                          List<Integer> voiceAnswerIndexes) {
        if (evaluation == null || evaluation.getQuestionDetails() == null) {
            return evaluation;
        }
        Set<Integer> voiceIndexes = voiceAnswerIndexes == null ? Set.of() : new HashSet<>(voiceAnswerIndexes);
        for (int i = 0; i < evaluation.getQuestionDetails().size(); i++) {
            InterviewEvaluationDTO.QuestionDetail detail = evaluation.getQuestionDetails().get(i);
            if (detail == null) {
                continue;
            }
            int questionIndex = detail.getQuestionIndex() == null ? i : detail.getQuestionIndex();
            boolean voiceAnswer = voiceIndexes.contains(questionIndex);
            detail.setAnswerMode(voiceAnswer ? "VOICE_TRANSCRIPT" : "TEXT");
            if (StringUtils.isBlank(detail.getContentIssue())) {
                detail.setContentIssue("暂无明显内容问题");
            }
            if (StringUtils.isBlank(detail.getExpressionIssue())) {
                detail.setExpressionIssue("暂无明显表达问题");
            }
            if (StringUtils.isBlank(detail.getStructureSuggestion())) {
                detail.setStructureSuggestion("建议按背景 -> 动作 -> 结果 -> 反思的顺序重讲，并先给结论。");
            }
            if (voiceAnswer) {
                if (StringUtils.isBlank(detail.getVoiceExpressionIssue())) {
                    detail.setVoiceExpressionIssue("转写文本未暴露明显语音表达问题。");
                }
                if (StringUtils.isBlank(detail.getVoiceExpressionSuggestion())) {
                    detail.setVoiceExpressionSuggestion("口头回答时先给结论，再按背景、动作、结果、反思展开。");
                }
            } else {
                detail.setVoiceExpressionIssue("非语音作答，无需语音表达建议");
                detail.setVoiceExpressionSuggestion("非语音作答，无需语音表达建议");
            }
        }
        return evaluation;
    }

}

