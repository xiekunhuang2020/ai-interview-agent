package com.xkh.ai.interview.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.entity.ResumeInfo;
import com.xkh.ai.interview.mapper.ResumeInfoMapper;
import com.xkh.ai.interview.service.dto.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class MockInterviewService {

    private static final Logger logger = LoggerFactory.getLogger(MockInterviewService.class);

    private static final String RESUME_CACHE_PREFIX = "ai:interview:resume:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final DashScopeChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ResumeInfoMapper resumeInfoMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("classpath:/prompt/resume-analysis-system.st")
    Resource resumeAnalysisSystemPromptResource;
    @Value("classpath:/prompt/interview-evaluation-system.st")
    Resource interviewEvaluationSystemPromptresource;
    @Value("classpath:/prompt/interview-question-system.st")
    Resource interviewQuestionsSystemPromptresource;
    @Value("classpath:/prompt/resume-analysis-user.st")
    Resource resumeAnalysisUserresource;

    public MockInterviewService(DashScopeChatModel chatModel, ObjectMapper objectMapper,
                                ResumeInfoMapper resumeInfoMapper, RedisTemplate<String, Object> redisTemplate) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.resumeInfoMapper = resumeInfoMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 评分简历
     */
    public ResumeScoreResult scoreResume(String resumeText) throws IOException {
        logger.info("开始评分简历，文本长度：{} 字符", resumeText.length());
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(resumeAnalysisSystemPromptResource));

        PromptTemplate promptTemplate = new PromptTemplate(resumeAnalysisUserresource.getContentAsString(StandardCharsets.UTF_8));
        messages.add(new UserMessage(promptTemplate.render(Map.of("resumeText", resumeText))));

        Prompt prompt = new Prompt(messages, DashScopeChatOptions.builder()
                .temperature(0.7)
                .build());

        String response = chatModel.call(prompt).getResult().getOutput().getText();
        logger.info("简历评分 AI 响应完成");

        return parseResumeScoreResult(response);
    }

    /**
     * 生成面试问题
     */
    public InterviewQuestions generateInterviewQuestions(String resumeText) throws JsonProcessingException {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(interviewQuestionsSystemPromptresource));

        String userPrompt = """
                请根据以下简历内容生成面试问题：
                
                ## 候选人简历
                %s
                """.formatted(resumeText);

        messages.add(new UserMessage(userPrompt));

        Prompt prompt = new Prompt(messages, DashScopeChatOptions.builder()
                .temperature(0.7)
                .build());

        String response = chatModel.call(prompt).getResult().getOutput().getText();
        logger.info("面试问题 AI 响应完成");

        return parseInterviewQuestions(response);
    }

    /**
     * 评估答案
     */
    public InterviewEvaluation evaluateAnswers(
            String resumeText,
            InterviewQuestions questions,
            Map<Integer, String> answers) throws JsonProcessingException {

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(interviewEvaluationSystemPromptresource));

        StringBuilder qaText = new StringBuilder();
        for (int i = 0; i < questions.getQuestions().size(); i++) {
            InterviewQuestions.Question q = questions.getQuestions().get(i);
            String answer = StringUtils.isBlank(answers.get(i)) ? "未作答" : answers.get(i);
            qaText.append("问题 %d [%s]: %s\n".formatted(i + 1, q.getType(), q.getQuestion()));
            qaText.append("候选人回答：%s\n\n".formatted(answer));
        }

        String userPrompt = """
                请评估以下面试问答：
                %s
                """.formatted(qaText.toString());

        messages.add(new UserMessage(userPrompt));

        Prompt prompt = new Prompt(messages, DashScopeChatOptions.builder()
                .temperature(0.7)
                .build());

        String response = chatModel.call(prompt).getResult().getOutput().getText();
        logger.info("答案评估 AI 响应完成");

        return parseInterviewEvaluation(response);
    }

    /**
     * 保存简历数据（持久化到MySQL + 缓存到Redis）
     */
    public void saveResume(String resumeId, String originalFileName, String resumeText, ResumeScoreResult scoreResult) {
        try {
            ResumeInfo entity = new ResumeInfo();
            entity.setResumeId(resumeId);
            entity.setOriginalFileName(originalFileName);
            entity.setResumeText(resumeText);
            entity.setOverallScore(scoreResult.getOverallScore());
            entity.setScoreDetailJson(objectMapper.writeValueAsString(scoreResult.getScoreDetail()));
            entity.setStrengthsJson(objectMapper.writeValueAsString(scoreResult.getStrengths()));
            entity.setSuggestionsJson(objectMapper.writeValueAsString(scoreResult.getSuggestions()));
            entity.setSummary(scoreResult.getSummary());

            resumeInfoMapper.insert(entity);

            // 同步写入Redis缓存
            cacheResumeData(resumeId, buildResumeData(entity));
            logger.info("简历保存成功，resumeId={}", resumeId);
        } catch (JsonProcessingException e) {
            logger.error("保存简历序列化失败", e);
            throw new RuntimeException("保存简历失败", e);
        }
    }

    /**
     * 保存面试问题
     */
    public void saveQuestions(String resumeId, InterviewQuestions questions) {
        try {
            ResumeInfo entity = resumeInfoMapper.selectById(resumeId);
            if (entity != null) {
                entity.setQuestionsJson(objectMapper.writeValueAsString(questions));
                resumeInfoMapper.updateById(entity);

                cacheResumeData(resumeId, buildResumeData(entity));
                logger.info("面试问题保存成功，resumeId={}", resumeId);
            }
        } catch (JsonProcessingException e) {
            logger.error("保存面试问题序列化失败", e);
            throw new RuntimeException("保存面试问题失败", e);
        }
    }

    /**
     * 保存评估结果
     */
    public void saveEvaluation(String resumeId, InterviewEvaluation evaluation) {
        try {
            ResumeInfo entity = resumeInfoMapper.selectById(resumeId);
            if (entity != null) {
                entity.setEvaluationJson(objectMapper.writeValueAsString(evaluation));
                resumeInfoMapper.updateById(entity);

                cacheResumeData(resumeId, buildResumeData(entity));
                logger.info("评估结果保存成功，resumeId={}", resumeId);
            }
        } catch (JsonProcessingException e) {
            logger.error("保存评估结果序列化失败", e);
            throw new RuntimeException("保存评估结果失败", e);
        }
    }

    /**
     * 获取简历数据（先查Redis缓存，未命中再查MySQL）
     */
    public ResumeData getResumeById(String resumeId) {
        String cacheKey = RESUME_CACHE_PREFIX + resumeId;

        // 1. 尝试从Redis获取
        ResumeData cached = (ResumeData) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            logger.debug("简历缓存命中，resumeId={}", resumeId);
            return cached;
        }

        // 2. 从MySQL查询并重建ResumeData
        ResumeInfo entity = resumeInfoMapper.selectById(resumeId);
        if (entity == null) {
            return null;
        }

        ResumeData data = buildResumeData(entity);

        // 3. 回写Redis缓存
        cacheResumeData(resumeId, data);
        return data;
    }

    private void cacheResumeData(String resumeId, ResumeData data) {
        String cacheKey = RESUME_CACHE_PREFIX + resumeId;
        redisTemplate.opsForValue().set(cacheKey, data, CACHE_TTL);
    }

    private ResumeData buildResumeData(ResumeInfo entity) {
        try {
            ResumeData data = new ResumeData();
            data.setResumeId(entity.getResumeId());
            data.setResumeText(entity.getResumeText());

            if (StringUtils.isNotBlank(entity.getScoreDetailJson())) {
                data.setScoreResult(ResumeScoreResult.builder()
                        .overallScore(entity.getOverallScore())
                        .scoreDetail(objectMapper.readValue(entity.getScoreDetailJson(), ResumeScoreResult.ScoreDetail.class))
                        .strengths(objectMapper.readValue(entity.getStrengthsJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)))
                        .suggestions(objectMapper.readValue(entity.getSuggestionsJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, ResumeScoreResult.Suggestion.class)))
                        .summary(entity.getSummary())
                        .build());
            }

            if (StringUtils.isNotBlank(entity.getQuestionsJson())) {
                data.setQuestions(objectMapper.readValue(entity.getQuestionsJson(), InterviewQuestions.class));
            }

            if (StringUtils.isNotBlank(entity.getEvaluationJson())) {
                data.setEvaluation(objectMapper.readValue(entity.getEvaluationJson(), InterviewEvaluation.class));
            }

            return data;
        } catch (JsonProcessingException e) {
            logger.error("构建ResumeData失败", e);
            throw new RuntimeException("数据解析失败", e);
        }
    }

    private ResumeScoreResult parseResumeScoreResult(String json) throws JsonProcessingException {
        json = cleanJsonResponse(json);

        try {
            JsonNode rootNode = objectMapper.readTree(json);

            Integer overallScore = rootNode.has("overallScore") ? rootNode.get("overallScore").asInt() : 0;
            String summary = rootNode.has("summary") ? rootNode.get("summary").asText() : "";

            ResumeScoreResult.ScoreDetail scoreDetail = new ResumeScoreResult.ScoreDetail();
            if (rootNode.has("scoreDetail")) {
                JsonNode detailNode = rootNode.get("scoreDetail");
                scoreDetail.setProjectScore(detailNode.has("projectScore") ? detailNode.get("projectScore").asInt() : 0);
                scoreDetail.setSkillMatchScore(detailNode.has("skillMatchScore") ? detailNode.get("skillMatchScore").asInt() : 0);
                scoreDetail.setContentScore(detailNode.has("contentScore") ? detailNode.get("contentScore").asInt() : 0);
                scoreDetail.setStructureScore(detailNode.has("structureScore") ? detailNode.get("structureScore").asInt() : 0);
                scoreDetail.setExpressionScore(detailNode.has("expressionScore") ? detailNode.get("expressionScore").asInt() : 0);
            }

            List<String> strengths = new ArrayList<>();
            if (rootNode.has("strengths") && rootNode.get("strengths").isArray()) {
                for (JsonNode item : rootNode.get("strengths")) {
                    strengths.add(item.asText());
                }
            }

            List<ResumeScoreResult.Suggestion> suggestions = new ArrayList<>();
            if (rootNode.has("suggestions") && rootNode.get("suggestions").isArray()) {
                for (JsonNode item : rootNode.get("suggestions")) {
                    ResumeScoreResult.Suggestion suggestion = new ResumeScoreResult.Suggestion();
                    suggestion.setCategory(item.has("category") ? item.get("category").asText() : "");
                    suggestion.setPriority(item.has("priority") ? item.get("priority").asText() : "");
                    suggestion.setIssue(item.has("issue") ? item.get("issue").asText() : "");
                    suggestion.setRecommendation(item.has("recommendation") ? item.get("recommendation").asText() : "");
                    suggestions.add(suggestion);
                }
            }

            return ResumeScoreResult.builder()
                    .overallScore(overallScore)
                    .scoreDetail(scoreDetail)
                    .summary(summary)
                    .strengths(strengths)
                    .suggestions(suggestions)
                    .build();
        } catch (Exception e) {
            logger.error("解析简历评分结果失败", e);
            throw new RuntimeException("解析失败：" + e.getMessage(), e);
        }
    }

    private InterviewQuestions parseInterviewQuestions(String json) throws JsonProcessingException {
        json = cleanJsonResponse(json);

        try {
            JsonNode rootNode = objectMapper.readTree(json);
            List<InterviewQuestions.Question> questions = new ArrayList<>();

            if (rootNode.has("questions") && rootNode.get("questions").isArray()) {
                for (JsonNode item : rootNode.get("questions")) {
                    InterviewQuestions.Question question = new InterviewQuestions.Question();
                    question.setQuestion(item.has("question") ? item.get("question").asText() : "");
                    question.setType(item.has("type") ? item.get("type").asText() : "");
                    question.setCategory(item.has("category") ? item.get("category").asText() : "");
                    questions.add(question);
                }
            }

            return InterviewQuestions.builder()
                    .questions(questions)
                    .build();
        } catch (Exception e) {
            logger.error("解析面试问题失败", e);
            throw new RuntimeException("解析失败：" + e.getMessage(), e);
        }
    }

    private InterviewEvaluation parseInterviewEvaluation(String json) throws JsonProcessingException {
        json = cleanJsonResponse(json);

        try {
            JsonNode rootNode = objectMapper.readTree(json);

            InterviewEvaluation.InterviewEvaluationBuilder builder = InterviewEvaluation.builder();

            builder.sessionId(rootNode.has("sessionId") ? rootNode.get("sessionId").asText() : UUID.randomUUID().toString());
            builder.totalQuestions(rootNode.has("totalQuestions") ? rootNode.get("totalQuestions").asInt() : 0);
            builder.overallScore(rootNode.has("overallScore") ? rootNode.get("overallScore").asInt() : 0);
            builder.overallFeedback(rootNode.has("overallFeedback") ? rootNode.get("overallFeedback").asText() : "");

            List<InterviewEvaluation.CategoryScore> categoryScores = new ArrayList<>();
            if (rootNode.has("categoryScores") && rootNode.get("categoryScores").isArray()) {
                for (JsonNode item : rootNode.get("categoryScores")) {
                    InterviewEvaluation.CategoryScore score = new InterviewEvaluation.CategoryScore();
                    score.setCategory(item.has("category") ? item.get("category").asText() : "");
                    score.setScore(item.has("score") ? item.get("score").asInt() : 0);
                    score.setQuestionCount(item.has("questionCount") ? item.get("questionCount").asInt() : 0);
                    categoryScores.add(score);
                }
            }
            builder.categoryScores(categoryScores);

            List<InterviewEvaluation.QuestionDetail> questionDetails = new ArrayList<>();
            if (rootNode.has("questionDetails") && rootNode.get("questionDetails").isArray()) {
                for (JsonNode item : rootNode.get("questionDetails")) {
                    InterviewEvaluation.QuestionDetail detail = new InterviewEvaluation.QuestionDetail();
                    detail.setQuestionIndex(item.has("questionIndex") ? item.get("questionIndex").asInt() : 0);
                    detail.setQuestion(item.has("question") ? item.get("question").asText() : "");
                    detail.setCategory(item.has("category") ? item.get("category").asText() : "");
                    detail.setUserAnswer(item.has("userAnswer") ? item.get("userAnswer").asText() : "");
                    detail.setScore(item.has("score") ? item.get("score").asInt() : 0);
                    detail.setFeedback(item.has("feedback") ? item.get("feedback").asText() : "");
                    questionDetails.add(detail);
                }
            }
            builder.questionDetails(questionDetails);

            List<String> strengths = new ArrayList<>();
            if (rootNode.has("strengths") && rootNode.get("strengths").isArray()) {
                for (JsonNode item : rootNode.get("strengths")) {
                    strengths.add(item.asText());
                }
            }
            builder.strengths(strengths);

            List<String> improvements = new ArrayList<>();
            if (rootNode.has("improvements") && rootNode.get("improvements").isArray()) {
                for (JsonNode item : rootNode.get("improvements")) {
                    improvements.add(item.asText());
                }
            }
            builder.improvements(improvements);

            List<InterviewEvaluation.ReferenceAnswer> referenceAnswers = new ArrayList<>();
            if (rootNode.has("referenceAnswers") && rootNode.get("referenceAnswers").isArray()) {
                for (JsonNode item : rootNode.get("referenceAnswers")) {
                    InterviewEvaluation.ReferenceAnswer answer = new InterviewEvaluation.ReferenceAnswer();
                    answer.setQuestionIndex(item.has("questionIndex") ? item.get("questionIndex").asInt() : 0);
                    answer.setQuestion(item.has("question") ? item.get("question").asText() : "");
                    answer.setReferenceAnswer(item.has("referenceAnswer") ? item.get("referenceAnswer").asText() : "");

                    List<String> keyPoints = new ArrayList<>();
                    if (item.has("keyPoints") && item.get("keyPoints").isArray()) {
                        for (JsonNode point : item.get("keyPoints")) {
                            keyPoints.add(point.asText());
                        }
                    }
                    answer.setKeyPoints(keyPoints);
                    referenceAnswers.add(answer);
                }
            }
            builder.referenceAnswers(referenceAnswers);

            return builder.build();
        } catch (Exception e) {
            logger.error("解析面试评估失败", e);
            throw new RuntimeException("解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 清理 JSON 响应，移除可能的 Markdown 标记
     */
    private String cleanJsonResponse(String json) {
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }

        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }

        return json.trim();
    }
}
