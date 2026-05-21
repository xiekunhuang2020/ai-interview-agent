package com.xkh.ai.interview.service.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.entity.InterviewEvaluationRecord;
import com.xkh.ai.interview.entity.InterviewQuestionRecord;
import com.xkh.ai.interview.entity.JobDescriptionMatchRecord;
import com.xkh.ai.interview.entity.ResumeInfo;
import com.xkh.ai.interview.entity.ResumeScoreRecord;
import com.xkh.ai.interview.mapper.InterviewEvaluationRecordMapper;
import com.xkh.ai.interview.mapper.InterviewQuestionRecordMapper;
import com.xkh.ai.interview.mapper.JobDescriptionMatchRecordMapper;
import com.xkh.ai.interview.mapper.ResumeInfoMapper;
import com.xkh.ai.interview.mapper.ResumeScoreRecordMapper;
import com.xkh.ai.interview.dto.InterviewEvaluation;
import com.xkh.ai.interview.dto.InterviewQuestions;
import com.xkh.ai.interview.dto.JobDescriptionMatchResult;
import com.xkh.ai.interview.dto.ResumeData;
import com.xkh.ai.interview.dto.ResumeScoreResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

@Component
public class ResumeRepositoryTool {

    private static final Logger logger = LoggerFactory.getLogger(ResumeRepositoryTool.class);
    private static final String RESUME_CACHE_PREFIX = "ai:interview:resume:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ResumeInfoMapper resumeInfoMapper;
    private final ResumeScoreRecordMapper resumeScoreRecordMapper;
    private final InterviewQuestionRecordMapper interviewQuestionRecordMapper;
    private final InterviewEvaluationRecordMapper interviewEvaluationRecordMapper;
    private final JobDescriptionMatchRecordMapper jobDescriptionMatchRecordMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public ResumeRepositoryTool(ResumeInfoMapper resumeInfoMapper,
                                ResumeScoreRecordMapper resumeScoreRecordMapper,
                                InterviewQuestionRecordMapper interviewQuestionRecordMapper,
                                InterviewEvaluationRecordMapper interviewEvaluationRecordMapper,
                                JobDescriptionMatchRecordMapper jobDescriptionMatchRecordMapper,
                                RedisTemplate<String, Object> redisTemplate,
                                ObjectMapper objectMapper) {
        this.resumeInfoMapper = resumeInfoMapper;
        this.resumeScoreRecordMapper = resumeScoreRecordMapper;
        this.interviewQuestionRecordMapper = interviewQuestionRecordMapper;
        this.interviewEvaluationRecordMapper = interviewEvaluationRecordMapper;
        this.jobDescriptionMatchRecordMapper = jobDescriptionMatchRecordMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存简历基础信息和评分拆分结果。
     */
    @Transactional
    public void saveAnalyzedResume(String resumeId, String originalFileName, String resumeText, ResumeScoreResult scoreResult) {
        try {
            ResumeInfo entity = new ResumeInfo();
            entity.setResumeId(resumeId);
            entity.setOriginalFileName(originalFileName);
            entity.setResumeText(resumeText);

            resumeInfoMapper.insert(entity);
            saveResumeScoreRecord(resumeId, scoreResult);
            cacheResumeData(resumeId, buildResumeData(entity));
            logger.info("Resume saved, resumeId={}", resumeId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存简历失败", e);
        }
    }

    /**
     * 保存面试问题明细，同一份简历只保留最新一组问题。
     */
    @Transactional
    public void saveQuestions(String resumeId, InterviewQuestions questions) {
        ResumeInfo entity = requireResumeInfo(resumeId);
        replaceQuestionRecords(resumeId, questions);
        cacheResumeData(resumeId, buildResumeData(entity));
        logger.info("Interview questions saved, resumeId={}", resumeId);
    }

    /**
     * 保存面试评估结果。
     */
    @Transactional
    public void saveEvaluation(String resumeId, InterviewEvaluation evaluation) {
        try {
            ResumeInfo entity = requireResumeInfo(resumeId);
            saveEvaluationRecord(resumeId, evaluation);
            cacheResumeData(resumeId, buildResumeData(entity));
            logger.info("Interview evaluation saved, resumeId={}", resumeId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存评估结果失败", e);
        }
    }

    /**
     * 保存最近一次岗位匹配结果，让匹配结果不再只依赖浏览器本地缓存。
     */
    @Transactional
    public void saveJobMatch(String resumeId, String jobDescription, JobDescriptionMatchResult matchResult) {
        try {
            ResumeInfo entity = requireResumeInfo(resumeId);
            JobDescriptionMatchRecord record = new JobDescriptionMatchRecord();
            record.setResumeId(resumeId);
            record.setJobDescription(jobDescription);
            record.setOverallScore(matchResult.getOverallScore());
            record.setMatchLevel(matchResult.getMatchLevel());
            record.setSummary(matchResult.getSummary());
            record.setMatchedSkillsJson(writeList(matchResult.getMatchedSkills()));
            record.setMissingSkillsJson(writeList(matchResult.getMissingSkills()));
            record.setInterviewFocusJson(writeList(matchResult.getInterviewFocus()));
            record.setRisksJson(writeList(matchResult.getRisks()));
            record.setLearningSuggestionsJson(writeList(matchResult.getLearningSuggestions()));
            upsertJobMatchRecord(record);
            cacheResumeData(resumeId, buildResumeData(entity));
            logger.info("Job match result saved, resumeId={}", resumeId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("保存岗位匹配结果失败", e);
        }
    }

    /**
     * 查询简历聚合数据。
     */
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

    /**
     * 查询简历主表记录，不存在时抛出明确异常。
     */
    private ResumeInfo requireResumeInfo(String resumeId) {
        ResumeInfo entity = resumeInfoMapper.selectById(resumeId);
        if (entity == null) {
            throw new NoSuchElementException("简历不存在：" + resumeId);
        }
        return entity;
    }

    /**
     * 新增或更新评分拆分表，便于后续按维度统计评分。
     */
    private void saveResumeScoreRecord(String resumeId, ResumeScoreResult scoreResult) throws JsonProcessingException {
        ResumeScoreResult.ScoreDetail detail = scoreResult.getScoreDetail();
        ResumeScoreRecord record = new ResumeScoreRecord();
        record.setResumeId(resumeId);
        record.setOverallScore(scoreResult.getOverallScore());
        record.setProjectScore(detail.getProjectScore());
        record.setSkillMatchScore(detail.getSkillMatchScore());
        record.setContentScore(detail.getContentScore());
        record.setStructureScore(detail.getStructureScore());
        record.setExpressionScore(detail.getExpressionScore());
        record.setSummary(scoreResult.getSummary());
        record.setStrengthsJson(writeList(scoreResult.getStrengths()));
        record.setSuggestionsJson(writeList(scoreResult.getSuggestions()));
        upsertResumeScoreRecord(record);
    }

    /**
     * 刷新面试问题明细表，同一份简历只保留最新一组问题。
     */
    private void replaceQuestionRecords(String resumeId, InterviewQuestions questions) {
        interviewQuestionRecordMapper.delete(new LambdaQueryWrapper<InterviewQuestionRecord>()
                .eq(InterviewQuestionRecord::getResumeId, resumeId));
        List<InterviewQuestions.Question> questionList = questions == null ? List.of() : safeList(questions.getQuestions());
        for (int index = 0; index < questionList.size(); index++) {
            InterviewQuestions.Question question = questionList.get(index);
            InterviewQuestionRecord record = new InterviewQuestionRecord();
            record.setResumeId(resumeId);
            record.setQuestionIndex(index);
            record.setQuestionText(question.getQuestion());
            record.setQuestionType(question.getType());
            record.setCategory(question.getCategory());
            interviewQuestionRecordMapper.insert(record);
        }
    }

    /**
     * 新增或更新评估拆分表，把总分、题量、整体反馈等高频字段独立出来。
     */
    private void saveEvaluationRecord(String resumeId, InterviewEvaluation evaluation) throws JsonProcessingException {
        InterviewEvaluationRecord record = new InterviewEvaluationRecord();
        record.setResumeId(resumeId);
        record.setSessionId(evaluation.getSessionId());
        record.setTotalQuestions(evaluation.getTotalQuestions());
        record.setOverallScore(evaluation.getOverallScore());
        record.setOverallFeedback(evaluation.getOverallFeedback());
        record.setCategoryScoresJson(writeList(evaluation.getCategoryScores()));
        record.setQuestionDetailsJson(writeList(evaluation.getQuestionDetails()));
        record.setStrengthsJson(writeList(evaluation.getStrengths()));
        record.setImprovementsJson(writeList(evaluation.getImprovements()));
        record.setReferenceAnswersJson(writeList(evaluation.getReferenceAnswers()));
        upsertEvaluationRecord(record);
    }

    /**
     * Upsert 简历评分记录，避免重复分析同一简历时主键冲突。
     */
    private void upsertResumeScoreRecord(ResumeScoreRecord record) {
        if (resumeScoreRecordMapper.selectById(record.getResumeId()) == null) {
            resumeScoreRecordMapper.insert(record);
            return;
        }
        resumeScoreRecordMapper.updateById(record);
    }

    /**
     * Upsert 面试评估记录，保证每份简历只保留最新一次评估。
     */
    private void upsertEvaluationRecord(InterviewEvaluationRecord record) {
        if (interviewEvaluationRecordMapper.selectById(record.getResumeId()) == null) {
            interviewEvaluationRecordMapper.insert(record);
            return;
        }
        interviewEvaluationRecordMapper.updateById(record);
    }

    /**
     * Upsert 岗位匹配记录，保证每份简历只保留最近一次 JD 匹配结果。
     */
    private void upsertJobMatchRecord(JobDescriptionMatchRecord record) {
        if (jobDescriptionMatchRecordMapper.selectById(record.getResumeId()) == null) {
            jobDescriptionMatchRecordMapper.insert(record);
            return;
        }
        jobDescriptionMatchRecordMapper.updateById(record);
    }

    /**
     * 写入 Redis 缓存，缓存失败不影响数据库主流程。
     */
    private void cacheResumeData(String resumeId, ResumeData data) {
        try {
            redisTemplate.opsForValue().set(cacheKey(resumeId), data, CACHE_TTL);
        } catch (RuntimeException e) {
            logger.warn("Resume cache write failed, resumeId={}, error={}", resumeId, e.getMessage());
        }
    }

    /**
     * 从 Redis 读取简历聚合缓存，缓存异常时回退数据库。
     */
    private ResumeData getCachedResumeData(String resumeId) {
        try {
            return (ResumeData) redisTemplate.opsForValue().get(cacheKey(resumeId));
        } catch (RuntimeException e) {
            logger.warn("Resume cache read failed, resumeId={}, error={}", resumeId, e.getMessage());
            return null;
        }
    }

    /**
     * 生成简历工作台缓存 Key。
     */
    private String cacheKey(String resumeId) {
        return RESUME_CACHE_PREFIX + resumeId;
    }

    /**
     * 组装前端工作台 DTO。
     */
    private ResumeData buildResumeData(ResumeInfo entity) {
        try {
            ResumeData data = new ResumeData();
            data.setResumeId(entity.getResumeId());
            data.setResumeText(entity.getResumeText());
            data.setScoreResult(readScoreResult(entity));
            data.setQuestions(readQuestions(entity));
            data.setEvaluation(readEvaluation(entity));
            applyJobMatch(data);
            return data;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("简历数据解析失败", e);
        }
    }

    /**
     * 从 resume_score 读取评分结果。
     */
    private ResumeScoreResult readScoreResult(ResumeInfo entity) throws JsonProcessingException {
        ResumeScoreRecord record = resumeScoreRecordMapper.selectById(entity.getResumeId());
        if (record == null) {
            return null;
        }
        return ResumeScoreResult.builder()
                .overallScore(record.getOverallScore())
                .scoreDetail(new ResumeScoreResult.ScoreDetail(
                        record.getProjectScore(),
                        record.getSkillMatchScore(),
                        record.getContentScore(),
                        record.getStructureScore(),
                        record.getExpressionScore()
                ))
                .strengths(readList(record.getStrengthsJson(), String.class))
                .suggestions(readList(record.getSuggestionsJson(), ResumeScoreResult.Suggestion.class))
                .summary(record.getSummary())
                .build();
    }

    /**
     * 从 interview_question 读取面试问题。
     */
    private InterviewQuestions readQuestions(ResumeInfo entity) throws JsonProcessingException {
        List<InterviewQuestionRecord> records = interviewQuestionRecordMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestionRecord>()
                        .eq(InterviewQuestionRecord::getResumeId, entity.getResumeId())
                        .orderByAsc(InterviewQuestionRecord::getQuestionIndex)
        );
        if (!records.isEmpty()) {
            List<InterviewQuestions.Question> questions = records.stream()
                    .map(record -> new InterviewQuestions.Question(
                            record.getQuestionText(),
                            record.getQuestionType(),
                            record.getCategory()
                    ))
                    .toList();
            return InterviewQuestions.builder().questions(questions).build();
        }
        return null;
    }

    /**
     * 从 interview_evaluation 读取评估结果。
     */
    private InterviewEvaluation readEvaluation(ResumeInfo entity) throws JsonProcessingException {
        InterviewEvaluationRecord record = interviewEvaluationRecordMapper.selectById(entity.getResumeId());
        if (record == null) {
            return null;
        }
        return InterviewEvaluation.builder()
                .sessionId(record.getSessionId())
                .totalQuestions(record.getTotalQuestions())
                .overallScore(record.getOverallScore())
                .overallFeedback(record.getOverallFeedback())
                .categoryScores(readList(record.getCategoryScoresJson(), InterviewEvaluation.CategoryScore.class))
                .questionDetails(readList(record.getQuestionDetailsJson(), InterviewEvaluation.QuestionDetail.class))
                .strengths(readList(record.getStrengthsJson(), String.class))
                .improvements(readList(record.getImprovementsJson(), String.class))
                .referenceAnswers(readList(record.getReferenceAnswersJson(), InterviewEvaluation.ReferenceAnswer.class))
                .build();
    }

    /**
     * 读取最近一次岗位匹配结果，并写入工作台 DTO。
     */
    private void applyJobMatch(ResumeData data) throws JsonProcessingException {
        JobDescriptionMatchRecord record = jobDescriptionMatchRecordMapper.selectById(data.getResumeId());
        if (record == null) {
            return;
        }
        data.setJobDescription(record.getJobDescription());
        data.setMatchResult(JobDescriptionMatchResult.builder()
                .overallScore(record.getOverallScore())
                .matchLevel(record.getMatchLevel())
                .summary(record.getSummary())
                .matchedSkills(readList(record.getMatchedSkillsJson(), JobDescriptionMatchResult.SkillMatch.class))
                .missingSkills(readList(record.getMissingSkillsJson(), JobDescriptionMatchResult.SkillGap.class))
                .interviewFocus(readList(record.getInterviewFocusJson(), String.class))
                .risks(readList(record.getRisksJson(), String.class))
                .learningSuggestions(readList(record.getLearningSuggestionsJson(), String.class))
                .build());
    }

    /**
     * 将列表写成 JSON，空值按空数组保存，避免读取时出现 null 列表。
     */
    private String writeList(List<?> values) throws JsonProcessingException {
        return objectMapper.writeValueAsString(values == null ? List.of() : values);
    }

    /**
     * 从 JSON 读取列表，空字段统一返回空列表。
     */
    private <T> List<T> readList(String json, Class<T> elementType) throws JsonProcessingException {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return objectMapper.readValue(json, listType);
    }

    /**
     * 将可能为空的列表转换为空列表，方便批量写入问题明细。
     */
    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
