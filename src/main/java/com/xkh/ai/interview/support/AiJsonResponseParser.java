package com.xkh.ai.interview.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.service.dto.InterviewEvaluation;
import com.xkh.ai.interview.service.dto.InterviewQuestions;
import com.xkh.ai.interview.service.dto.JobDescriptionMatchResult;
import com.xkh.ai.interview.service.dto.ResumeScoreResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class AiJsonResponseParser {

    private static final Logger logger = LoggerFactory.getLogger(AiJsonResponseParser.class);

    private static final Set<String> QUESTION_TYPES = Set.of(
            "PROJECT", "JAVA_BASIC", "JAVA_COLLECTION", "JAVA_CONCURRENT",
            "MYSQL", "REDIS", "SPRING", "SPRING_BOOT", "AI"
    );

    private static final Set<String> PRIORITIES = Set.of("高", "中", "低");
    private static final Set<String> MATCH_LEVELS = Set.of("高度匹配", "较匹配", "一般匹配", "匹配度较低");
    private static final Set<String> IMPORTANCE_LEVELS = Set.of("高", "中", "低");

    private final ObjectMapper objectMapper;

    public AiJsonResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResumeScoreResult parseResumeScoreResult(String json) throws JsonProcessingException {
        JsonNode rootNode = readRootObject(json, "resume-score");
        requireFields(rootNode, "resume-score", "overallScore", "scoreDetail", "summary", "strengths", "suggestions");
        validateResumeScoreSchema(rootNode);

        Integer overallScore = rootNode.has("overallScore") ? rootNode.get("overallScore").asInt() : 0;
        String summary = rootNode.has("summary") ? rootNode.get("summary").asText() : "";

        ResumeScoreResult.ScoreDetail scoreDetail = new ResumeScoreResult.ScoreDetail();
        if (rootNode.has("scoreDetail")) {
            JsonNode detailNode = rootNode.get("scoreDetail");
            scoreDetail.setProjectScore(readObjectClampedInteger(detailNode, "resume-score",
                    "scoreDetail", "projectScore", 0, 40));
            scoreDetail.setSkillMatchScore(readObjectClampedInteger(detailNode, "resume-score",
                    "scoreDetail", "skillMatchScore", 0, 20));
            scoreDetail.setContentScore(readObjectClampedInteger(detailNode, "resume-score",
                    "scoreDetail", "contentScore", 0, 15));
            scoreDetail.setStructureScore(readObjectClampedInteger(detailNode, "resume-score",
                    "scoreDetail", "structureScore", 0, 15));
            scoreDetail.setExpressionScore(readObjectClampedInteger(detailNode, "resume-score",
                    "scoreDetail", "expressionScore", 0, 10));
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
    }

    public InterviewQuestions parseInterviewQuestions(String json) throws JsonProcessingException {
        JsonNode rootNode = readRootObject(json, "interview-questions");
        requireFields(rootNode, "interview-questions", "questions");
        validateInterviewQuestionsSchema(rootNode);
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
    }

    public InterviewEvaluation parseInterviewEvaluation(String json) throws JsonProcessingException {
        JsonNode rootNode = readRootObject(json, "interview-evaluation");
        requireFields(rootNode, "interview-evaluation",
                "sessionId", "totalQuestions", "overallScore", "categoryScores", "questionDetails",
                "overallFeedback", "strengths", "improvements", "referenceAnswers");
        validateInterviewEvaluationSchema(rootNode);

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
    }

    public JobDescriptionMatchResult parseJobDescriptionMatchResult(String json) throws JsonProcessingException {
        JsonNode rootNode = readRootObject(json, "jd-match");
        requireFields(rootNode, "jd-match",
                "overallScore", "matchLevel", "summary", "matchedSkills", "missingSkills",
                "interviewFocus", "risks", "learningSuggestions");
        validateJobDescriptionMatchSchema(rootNode);

        List<JobDescriptionMatchResult.SkillMatch> matchedSkills = new ArrayList<>();
        if (rootNode.has("matchedSkills") && rootNode.get("matchedSkills").isArray()) {
            for (JsonNode item : rootNode.get("matchedSkills")) {
                JobDescriptionMatchResult.SkillMatch skillMatch = new JobDescriptionMatchResult.SkillMatch();
                skillMatch.setSkill(item.has("skill") ? item.get("skill").asText() : "");
                skillMatch.setEvidence(item.has("evidence") ? item.get("evidence").asText() : "");
                skillMatch.setScore(item.has("score") ? item.get("score").asInt() : 0);
                matchedSkills.add(skillMatch);
            }
        }

        List<JobDescriptionMatchResult.SkillGap> missingSkills = new ArrayList<>();
        if (rootNode.has("missingSkills") && rootNode.get("missingSkills").isArray()) {
            for (JsonNode item : rootNode.get("missingSkills")) {
                JobDescriptionMatchResult.SkillGap skillGap = new JobDescriptionMatchResult.SkillGap();
                skillGap.setSkill(item.has("skill") ? item.get("skill").asText() : "");
                skillGap.setImportance(item.has("importance") ? item.get("importance").asText() : "");
                skillGap.setSuggestion(item.has("suggestion") ? item.get("suggestion").asText() : "");
                missingSkills.add(skillGap);
            }
        }

        return JobDescriptionMatchResult.builder()
                .overallScore(rootNode.has("overallScore") ? rootNode.get("overallScore").asInt() : 0)
                .matchLevel(rootNode.has("matchLevel") ? rootNode.get("matchLevel").asText() : "")
                .summary(rootNode.has("summary") ? rootNode.get("summary").asText() : "")
                .matchedSkills(matchedSkills)
                .missingSkills(missingSkills)
                .interviewFocus(readStringList(rootNode, "interviewFocus"))
                .risks(readStringList(rootNode, "risks"))
                .learningSuggestions(readStringList(rootNode, "learningSuggestions"))
                .build();
    }

    private List<String> readStringList(JsonNode rootNode, String fieldName) {
        List<String> values = new ArrayList<>();
        if (rootNode.has(fieldName) && rootNode.get(fieldName).isArray()) {
            for (JsonNode item : rootNode.get(fieldName)) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private void validateResumeScoreSchema(JsonNode rootNode) {
        String schemaName = "resume-score";
        requireOnlyKnownFields(rootNode, schemaName, "$",
                "overallScore", "scoreDetail", "summary", "strengths", "suggestions");
        requireIntegerRange(rootNode, schemaName, "overallScore", 0, 100);
        requireObject(rootNode, schemaName, "scoreDetail");
        requireString(rootNode, schemaName, "summary");
        requireStringArray(rootNode, schemaName, "strengths");
        requireObjectArray(rootNode, schemaName, "suggestions");

        JsonNode scoreDetail = rootNode.get("scoreDetail");
        requireObjectFields(scoreDetail, schemaName, "scoreDetail",
                "projectScore", "skillMatchScore", "contentScore", "structureScore", "expressionScore");
        requireOnlyKnownFields(scoreDetail, schemaName, "scoreDetail",
                "projectScore", "skillMatchScore", "contentScore", "structureScore", "expressionScore");
        requireObjectInteger(scoreDetail, schemaName, "scoreDetail", "projectScore");
        requireObjectInteger(scoreDetail, schemaName, "scoreDetail", "skillMatchScore");
        requireObjectInteger(scoreDetail, schemaName, "scoreDetail", "contentScore");
        requireObjectInteger(scoreDetail, schemaName, "scoreDetail", "structureScore");
        requireObjectInteger(scoreDetail, schemaName, "scoreDetail", "expressionScore");

        JsonNode suggestions = rootNode.get("suggestions");
        for (int i = 0; i < suggestions.size(); i++) {
            JsonNode item = suggestions.get(i);
            String path = "suggestions[" + i + "]";
            requireObjectFields(item, schemaName, path, "category", "priority", "issue", "recommendation");
            requireOnlyKnownFields(item, schemaName, path, "category", "priority", "issue", "recommendation");
            requireObjectString(item, schemaName, path, "category");
            requireObjectEnum(item, schemaName, path, "priority", PRIORITIES);
            requireObjectString(item, schemaName, path, "issue");
            requireObjectString(item, schemaName, path, "recommendation");
        }
    }

    private void validateInterviewQuestionsSchema(JsonNode rootNode) {
        String schemaName = "interview-questions";
        requireOnlyKnownFields(rootNode, schemaName, "$", "questions");
        requireObjectArray(rootNode, schemaName, "questions");

        JsonNode questions = rootNode.get("questions");
        for (int i = 0; i < questions.size(); i++) {
            JsonNode item = questions.get(i);
            String path = "questions[" + i + "]";
            requireObjectFields(item, schemaName, path, "question", "type", "category");
            requireOnlyKnownFields(item, schemaName, path, "question", "type", "category");
            requireObjectString(item, schemaName, path, "question");
            requireObjectEnum(item, schemaName, path, "type", QUESTION_TYPES);
            requireObjectString(item, schemaName, path, "category");
        }
    }

    private void validateInterviewEvaluationSchema(JsonNode rootNode) {
        String schemaName = "interview-evaluation";
        requireOnlyKnownFields(rootNode, schemaName, "$",
                "sessionId", "totalQuestions", "overallScore", "categoryScores", "questionDetails",
                "overallFeedback", "strengths", "improvements", "referenceAnswers");
        requireString(rootNode, schemaName, "sessionId");
        requireIntegerRange(rootNode, schemaName, "totalQuestions", 0, 200);
        requireIntegerRange(rootNode, schemaName, "overallScore", 0, 100);
        requireObjectArray(rootNode, schemaName, "categoryScores");
        requireObjectArray(rootNode, schemaName, "questionDetails");
        requireString(rootNode, schemaName, "overallFeedback");
        requireStringArray(rootNode, schemaName, "strengths");
        requireStringArray(rootNode, schemaName, "improvements");
        requireObjectArray(rootNode, schemaName, "referenceAnswers");

        JsonNode categoryScores = rootNode.get("categoryScores");
        for (int i = 0; i < categoryScores.size(); i++) {
            JsonNode item = categoryScores.get(i);
            String path = "categoryScores[" + i + "]";
            requireObjectFields(item, schemaName, path, "category", "score", "questionCount");
            requireOnlyKnownFields(item, schemaName, path, "category", "score", "questionCount");
            requireObjectString(item, schemaName, path, "category");
            requireObjectIntegerRange(item, schemaName, path, "score", 0, 100);
            requireObjectIntegerRange(item, schemaName, path, "questionCount", 0, 200);
        }

        JsonNode questionDetails = rootNode.get("questionDetails");
        for (int i = 0; i < questionDetails.size(); i++) {
            JsonNode item = questionDetails.get(i);
            String path = "questionDetails[" + i + "]";
            requireObjectFields(item, schemaName, path,
                    "questionIndex", "question", "category", "userAnswer", "score", "feedback");
            requireOnlyKnownFields(item, schemaName, path,
                    "questionIndex", "question", "category", "userAnswer", "score", "feedback");
            requireObjectIntegerRange(item, schemaName, path, "questionIndex", 0, 200);
            requireObjectString(item, schemaName, path, "question");
            requireObjectString(item, schemaName, path, "category");
            requireObjectString(item, schemaName, path, "userAnswer");
            requireObjectIntegerRange(item, schemaName, path, "score", 0, 100);
            requireObjectString(item, schemaName, path, "feedback");
        }

        JsonNode referenceAnswers = rootNode.get("referenceAnswers");
        for (int i = 0; i < referenceAnswers.size(); i++) {
            JsonNode item = referenceAnswers.get(i);
            String path = "referenceAnswers[" + i + "]";
            requireObjectFields(item, schemaName, path, "questionIndex", "question", "referenceAnswer", "keyPoints");
            requireOnlyKnownFields(item, schemaName, path, "questionIndex", "question", "referenceAnswer", "keyPoints");
            requireObjectIntegerRange(item, schemaName, path, "questionIndex", 0, 200);
            requireObjectString(item, schemaName, path, "question");
            requireObjectString(item, schemaName, path, "referenceAnswer");
            requireObjectStringArray(item, schemaName, path, "keyPoints");
        }
    }

    private void validateJobDescriptionMatchSchema(JsonNode rootNode) {
        String schemaName = "jd-match";
        requireOnlyKnownFields(rootNode, schemaName, "$",
                "overallScore", "matchLevel", "summary", "matchedSkills", "missingSkills",
                "interviewFocus", "risks", "learningSuggestions");
        requireIntegerRange(rootNode, schemaName, "overallScore", 0, 100);
        requireEnum(rootNode, schemaName, "matchLevel", MATCH_LEVELS);
        requireString(rootNode, schemaName, "summary");
        requireObjectArray(rootNode, schemaName, "matchedSkills");
        requireObjectArray(rootNode, schemaName, "missingSkills");
        requireStringArray(rootNode, schemaName, "interviewFocus");
        requireStringArray(rootNode, schemaName, "risks");
        requireStringArray(rootNode, schemaName, "learningSuggestions");

        JsonNode matchedSkills = rootNode.get("matchedSkills");
        for (int i = 0; i < matchedSkills.size(); i++) {
            JsonNode item = matchedSkills.get(i);
            String path = "matchedSkills[" + i + "]";
            requireObjectFields(item, schemaName, path, "skill", "evidence", "score");
            requireOnlyKnownFields(item, schemaName, path, "skill", "evidence", "score");
            requireObjectString(item, schemaName, path, "skill");
            requireObjectString(item, schemaName, path, "evidence");
            requireObjectIntegerRange(item, schemaName, path, "score", 0, 100);
        }

        JsonNode missingSkills = rootNode.get("missingSkills");
        for (int i = 0; i < missingSkills.size(); i++) {
            JsonNode item = missingSkills.get(i);
            String path = "missingSkills[" + i + "]";
            requireObjectFields(item, schemaName, path, "skill", "importance", "suggestion");
            requireOnlyKnownFields(item, schemaName, path, "skill", "importance", "suggestion");
            requireObjectString(item, schemaName, path, "skill");
            requireObjectEnum(item, schemaName, path, "importance", IMPORTANCE_LEVELS);
            requireObjectString(item, schemaName, path, "suggestion");
        }
    }

    private JsonNode readRootObject(String json, String schemaName) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(cleanJsonResponse(json));
        if (rootNode == null || !rootNode.isObject()) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：根节点必须是 JSON 对象");
        }
        return rootNode;
    }

    private void requireFields(JsonNode rootNode, String schemaName, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (!rootNode.has(fieldName) || rootNode.get(fieldName).isNull()) {
                throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：缺少字段 " + fieldName);
            }
        }
    }

    private void requireArray(JsonNode rootNode, String schemaName, String fieldName) {
        if (!rootNode.has(fieldName) || !rootNode.get(fieldName).isArray()) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + fieldName + " 必须是数组");
        }
    }

    private void requireObject(JsonNode rootNode, String schemaName, String fieldName) {
        if (!rootNode.has(fieldName) || !rootNode.get(fieldName).isObject()) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + fieldName + " 必须是对象");
        }
    }

    private void requireString(JsonNode rootNode, String schemaName, String fieldName) {
        if (!rootNode.has(fieldName) || !rootNode.get(fieldName).isTextual()) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + fieldName + " 必须是字符串");
        }
    }

    private void requireInteger(JsonNode rootNode, String schemaName, String fieldName) {
        if (!rootNode.has(fieldName) || !rootNode.get(fieldName).canConvertToInt()) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + fieldName + " 必须是整数");
        }
    }

    private void requireIntegerRange(JsonNode rootNode, String schemaName, String fieldName, int min, int max) {
        requireInteger(rootNode, schemaName, fieldName);
        int value = rootNode.get(fieldName).asInt();
        if (value < min || value > max) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + fieldName + " 必须在 " + min + "-" + max + " 范围内");
        }
    }

    private void requireEnum(JsonNode rootNode, String schemaName, String fieldName, Set<String> allowedValues) {
        requireString(rootNode, schemaName, fieldName);
        String value = rootNode.get(fieldName).asText();
        if (!allowedValues.contains(value)) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + fieldName + " 非法枚举值 " + value);
        }
    }

    private void requireStringArray(JsonNode rootNode, String schemaName, String fieldName) {
        requireArray(rootNode, schemaName, fieldName);
        JsonNode arrayNode = rootNode.get(fieldName);
        for (int i = 0; i < arrayNode.size(); i++) {
            if (!arrayNode.get(i).isTextual()) {
                throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + fieldName + "[" + i + "] 必须是字符串");
            }
        }
    }

    private void requireObjectArray(JsonNode rootNode, String schemaName, String fieldName) {
        requireArray(rootNode, schemaName, fieldName);
        JsonNode arrayNode = rootNode.get(fieldName);
        for (int i = 0; i < arrayNode.size(); i++) {
            if (!arrayNode.get(i).isObject()) {
                throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + fieldName + "[" + i + "] 必须是对象");
            }
        }
    }

    private void requireObjectFields(JsonNode objectNode, String schemaName, String path, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (!objectNode.has(fieldName) || objectNode.get(fieldName).isNull()) {
                throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：缺少字段 " + path + "." + fieldName);
            }
        }
    }

    private void requireObjectString(JsonNode objectNode, String schemaName, String path, String fieldName) {
        if (!objectNode.has(fieldName) || !objectNode.get(fieldName).isTextual()) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + path + "." + fieldName + " 必须是字符串");
        }
    }

    private void requireObjectInteger(JsonNode objectNode, String schemaName, String path, String fieldName) {
        if (!objectNode.has(fieldName) || !objectNode.get(fieldName).canConvertToInt()) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + path + "." + fieldName + " 必须是整数");
        }
    }

    private void requireObjectIntegerRange(JsonNode objectNode, String schemaName, String path, String fieldName, int min, int max) {
        if (!objectNode.has(fieldName) || !objectNode.get(fieldName).canConvertToInt()) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + path + "." + fieldName + " 必须是整数");
        }
        int value = objectNode.get(fieldName).asInt();
        if (value < min || value > max) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + path + "." + fieldName + " 必须在 " + min + "-" + max + " 范围内");
        }
    }

    private void requireObjectEnum(JsonNode objectNode, String schemaName, String path, String fieldName, Set<String> allowedValues) {
        requireObjectString(objectNode, schemaName, path, fieldName);
        String value = objectNode.get(fieldName).asText();
        if (!allowedValues.contains(value)) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + path + "." + fieldName + " 非法枚举值 " + value);
        }
    }

    private void requireObjectStringArray(JsonNode objectNode, String schemaName, String path, String fieldName) {
        if (!objectNode.has(fieldName) || !objectNode.get(fieldName).isArray()) {
            throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + path + "." + fieldName + " 必须是数组");
        }
        JsonNode arrayNode = objectNode.get(fieldName);
        for (int i = 0; i < arrayNode.size(); i++) {
            if (!arrayNode.get(i).isTextual()) {
                throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + path + "." + fieldName + "[" + i + "] 必须是字符串");
            }
        }
    }

    private void requireOnlyKnownFields(JsonNode objectNode, String schemaName, String path, String... allowedFields) {
        Set<String> allowed = Set.copyOf(Arrays.asList(allowedFields));
        objectNode.fieldNames().forEachRemaining(fieldName -> {
            if (!allowed.contains(fieldName)) {
                throw new AiStructuredOutputException("AI 输出不符合 " + schemaName + " 结构：" + path + " 包含未知字段 " + fieldName);
            }
        });
    }

    private int readObjectClampedInteger(JsonNode objectNode,
                                         String schemaName,
                                         String path,
                                         String fieldName,
                                         int min,
                                         int max) {
        int value = objectNode.get(fieldName).asInt();
        if (value >= min && value <= max) {
            return value;
        }

        int clamped = Math.max(min, Math.min(max, value));
        logger.warn("AI score field out of range, schema={}, path={}.{}, value={}, normalized={}, min={}, max={}",
                schemaName, path, fieldName, value, clamped, min, max);
        return clamped;
    }

    private String cleanJsonResponse(String json) {
        String cleaned = json == null ? "" : json.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}
