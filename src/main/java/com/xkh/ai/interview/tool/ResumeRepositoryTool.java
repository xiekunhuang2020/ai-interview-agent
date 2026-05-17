package com.xkh.ai.interview.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.entity.ResumeInfo;
import com.xkh.ai.interview.mapper.ResumeInfoMapper;
import com.xkh.ai.interview.service.dto.InterviewEvaluation;
import com.xkh.ai.interview.service.dto.InterviewQuestions;
import com.xkh.ai.interview.service.dto.ResumeData;
import com.xkh.ai.interview.service.dto.ResumeScoreResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Component
public class ResumeRepositoryTool {

    private static final Logger logger = LoggerFactory.getLogger(ResumeRepositoryTool.class);
    private static final String RESUME_CACHE_PREFIX = "ai:interview:resume:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ResumeInfoMapper resumeInfoMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public ResumeRepositoryTool(ResumeInfoMapper resumeInfoMapper,
                                RedisTemplate<String, Object> redisTemplate,
                                ObjectMapper objectMapper) {
        this.resumeInfoMapper = resumeInfoMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveAnalyzedResume(String resumeId, String originalFileName, String resumeText, ResumeScoreResult scoreResult) {
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
            cacheResumeData(resumeId, buildResumeData(entity));
            logger.info("Resume saved, resumeId={}", resumeId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存简历失败", e);
        }
    }

    @Transactional
    public void saveQuestions(String resumeId, InterviewQuestions questions) {
        try {
            ResumeInfo entity = resumeInfoMapper.selectById(resumeId);
            if (entity == null) {
                return;
            }
            entity.setQuestionsJson(objectMapper.writeValueAsString(questions));
            resumeInfoMapper.updateById(entity);
            cacheResumeData(resumeId, buildResumeData(entity));
            logger.info("Interview questions saved, resumeId={}", resumeId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存面试问题失败", e);
        }
    }

    @Transactional
    public void saveEvaluation(String resumeId, InterviewEvaluation evaluation) {
        try {
            ResumeInfo entity = resumeInfoMapper.selectById(resumeId);
            if (entity == null) {
                return;
            }
            entity.setEvaluationJson(objectMapper.writeValueAsString(evaluation));
            resumeInfoMapper.updateById(entity);
            cacheResumeData(resumeId, buildResumeData(entity));
            logger.info("Interview evaluation saved, resumeId={}", resumeId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存评估结果失败", e);
        }
    }

    public ResumeData findById(String resumeId) {
        ResumeData cached = getCachedResumeData(resumeId);
        if (cached != null) {
            logger.debug("Resume cache hit, resumeId={}", resumeId);
            return cached;
        }

        ResumeInfo entity = resumeInfoMapper.selectById(resumeId);
        if (entity == null) {
            return null;
        }

        ResumeData data = buildResumeData(entity);
        cacheResumeData(resumeId, data);
        return data;
    }

    private void cacheResumeData(String resumeId, ResumeData data) {
        try {
            redisTemplate.opsForValue().set(cacheKey(resumeId), data, CACHE_TTL);
        } catch (RuntimeException e) {
            logger.warn("Resume cache write failed, resumeId={}, error={}", resumeId, e.getMessage());
        }
    }

    private ResumeData getCachedResumeData(String resumeId) {
        try {
            return (ResumeData) redisTemplate.opsForValue().get(cacheKey(resumeId));
        } catch (RuntimeException e) {
            logger.warn("Resume cache read failed, resumeId={}, error={}", resumeId, e.getMessage());
            return null;
        }
    }

    private String cacheKey(String resumeId) {
        return RESUME_CACHE_PREFIX + resumeId;
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
                        .strengths(readList(entity.getStrengthsJson(), String.class))
                        .suggestions(readList(entity.getSuggestionsJson(), ResumeScoreResult.Suggestion.class))
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
            throw new IllegalStateException("简历数据解析失败", e);
        }
    }

    private <T> List<T> readList(String json, Class<T> elementType) throws JsonProcessingException {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return objectMapper.readValue(json, listType);
    }
}
