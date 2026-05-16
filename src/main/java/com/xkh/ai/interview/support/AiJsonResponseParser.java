package com.xkh.ai.interview.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.service.dto.InterviewEvaluation;
import com.xkh.ai.interview.service.dto.InterviewQuestions;
import com.xkh.ai.interview.service.dto.JobDescriptionMatchResult;
import com.xkh.ai.interview.service.dto.ResumeScoreResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class AiJsonResponseParser {

    private final ObjectMapper objectMapper;

    public AiJsonResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResumeScoreResult parseResumeScoreResult(String json) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(cleanJsonResponse(json));

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
    }

    public InterviewQuestions parseInterviewQuestions(String json) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(cleanJsonResponse(json));
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
        JsonNode rootNode = objectMapper.readTree(cleanJsonResponse(json));

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
        JsonNode rootNode = objectMapper.readTree(cleanJsonResponse(json));

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
