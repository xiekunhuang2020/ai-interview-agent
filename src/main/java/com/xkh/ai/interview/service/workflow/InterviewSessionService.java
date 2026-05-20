package com.xkh.ai.interview.service.workflow;

import com.xkh.ai.interview.dto.InterviewSessionInfo;
import com.xkh.ai.interview.dto.ResumeData;
import com.xkh.ai.interview.entity.InterviewSession;
import com.xkh.ai.interview.mapper.InterviewSessionMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class InterviewSessionService {

    private static final int MAX_FAILED_REASON_LENGTH = 500;

    private static final Map<String, String> STAGE_TEXT = Map.of(
            "UPLOAD", "简历上传",
            "RESUME_ANALYSIS", "简历诊断",
            "JD_MATCH", "岗位匹配",
            "QUESTION_GENERATION", "面试题生成",
            "ANSWER_SUBMIT", "答案提交",
            "ANSWER_EVALUATION", "面试复盘"
    );

    private final InterviewSessionMapper interviewSessionMapper;

    /**
     * 注入面试会话 Mapper，用于维护每份简历的流程状态。
     */
    public InterviewSessionService(InterviewSessionMapper interviewSessionMapper) {
        this.interviewSessionMapper = interviewSessionMapper;
    }

    /**
     * 标记简历已上传并进入后续诊断流程。
     */
    @Transactional
    public void markUploaded(String resumeId, String originalFileName) {
        upsert(resumeId, originalFileName, InterviewSessionStatus.UPLOADED, "UPLOAD", null, null);
    }

    /**
     * 标记简历诊断已经完成。
     */
    @Transactional
    public void markAnalyzed(String resumeId, String originalFileName) {
        upsert(resumeId, originalFileName, InterviewSessionStatus.ANALYZED, "RESUME_ANALYSIS", null, null);
    }

    /**
     * 标记目标岗位匹配已经完成。
     */
    @Transactional
    public void markJobMatched(String resumeId) {
        upsert(resumeId, null, InterviewSessionStatus.JD_MATCHED, "JD_MATCH", null, null);
    }

    /**
     * 标记面试题已经生成。
     */
    @Transactional
    public void markQuestionsGenerated(String resumeId) {
        upsert(resumeId, null, InterviewSessionStatus.QUESTIONS_GENERATED, "QUESTION_GENERATION", null, null);
    }

    /**
     * 标记候选人答案已经提交，等待模型生成复盘结果。
     */
    @Transactional
    public void markAnswerSubmitted(String resumeId) {
        upsert(resumeId, null, InterviewSessionStatus.ANSWER_SUBMITTED, "ANSWER_SUBMIT", null, null);
    }

    /**
     * 标记面试复盘评估已经完成。
     */
    @Transactional
    public void markEvaluated(String resumeId) {
        upsert(resumeId, null, InterviewSessionStatus.EVALUATED, "ANSWER_EVALUATION", null, null);
    }

    /**
     * 标记流程失败，并记录失败阶段和可读原因。
     */
    @Transactional
    public void markFailed(String resumeId, String failedStage, Throwable error) {
        if (StringUtils.isBlank(resumeId)) {
            return;
        }
        upsert(resumeId, null, InterviewSessionStatus.FAILED, failedStage, failedStage, failedReason(error));
    }

    /**
     * 查询面试会话状态；旧数据没有会话记录时，根据简历已有结果推断一个只读状态。
     */
    public InterviewSessionInfo findInfo(String resumeId, ResumeData resumeData) {
        InterviewSession session = interviewSessionMapper.selectById(resumeId);
        if (session != null) {
            return toInfo(session);
        }
        if (resumeData == null) {
            return null;
        }
        return inferInfo(resumeId, resumeData);
    }

    /**
     * 新增或更新会话状态，清理已恢复流程上的旧失败信息。
     */
    private void upsert(String resumeId,
                        String originalFileName,
                        InterviewSessionStatus status,
                        String currentStage,
                        String failedStage,
                        String failedReason) {
        InterviewSession session = interviewSessionMapper.selectById(resumeId);
        if (session == null) {
            session = new InterviewSession();
            session.setResumeId(resumeId);
            session.setOriginalFileName(originalFileName);
            session.setStatus(status.name());
            session.setCurrentStage(currentStage);
            session.setFailedStage(failedStage);
            session.setFailedReason(failedReason);
            interviewSessionMapper.insert(session);
            return;
        }

        if (StringUtils.isNotBlank(originalFileName)) {
            session.setOriginalFileName(originalFileName);
        }
        session.setStatus(status.name());
        session.setCurrentStage(currentStage);
        session.setFailedStage(failedStage);
        session.setFailedReason(failedReason);
        interviewSessionMapper.updateById(session);
    }

    /**
     * 将数据库实体转换成前端可直接展示的状态 DTO。
     */
    private InterviewSessionInfo toInfo(InterviewSession session) {
        return InterviewSessionInfo.builder()
                .resumeId(session.getResumeId())
                .status(session.getStatus())
                .statusText(statusText(session.getStatus()))
                .currentStage(session.getCurrentStage())
                .currentStageText(stageText(session.getCurrentStage()))
                .failedStage(session.getFailedStage())
                .failedReason(session.getFailedReason())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .build();
    }

    /**
     * 根据旧简历记录中已有的问题和评估结果推断一个展示用状态。
     */
    private InterviewSessionInfo inferInfo(String resumeId, ResumeData resumeData) {
        InterviewSessionStatus status = InterviewSessionStatus.ANALYZED;
        String stage = "RESUME_ANALYSIS";
        if (resumeData.getEvaluation() != null) {
            status = InterviewSessionStatus.EVALUATED;
            stage = "ANSWER_EVALUATION";
        } else if (resumeData.getQuestions() != null
                && resumeData.getQuestions().getQuestions() != null
                && !resumeData.getQuestions().getQuestions().isEmpty()) {
            status = InterviewSessionStatus.QUESTIONS_GENERATED;
            stage = "QUESTION_GENERATION";
        }

        return InterviewSessionInfo.builder()
                .resumeId(resumeId)
                .status(status.name())
                .statusText(status.getText())
                .currentStage(stage)
                .currentStageText(stageText(stage))
                .build();
    }

    /**
     * 将状态码转换成中文展示文案，未知状态直接展示原值。
     */
    private String statusText(String status) {
        if (StringUtils.isBlank(status)) {
            return "--";
        }
        try {
            return InterviewSessionStatus.valueOf(status).getText();
        } catch (IllegalArgumentException e) {
            return status;
        }
    }

    /**
     * 将流程阶段码转换成中文展示文案，未知阶段直接展示原值。
     */
    private String stageText(String stage) {
        if (StringUtils.isBlank(stage)) {
            return "--";
        }
        return STAGE_TEXT.getOrDefault(stage, stage);
    }

    /**
     * 提取并裁剪异常原因，避免过长堆栈写入状态表。
     */
    private String failedReason(Throwable error) {
        if (error == null || StringUtils.isBlank(error.getMessage())) {
            return "未知错误";
        }
        String message = error.getMessage().replaceAll("\\s+", " ").trim();
        if (message.length() <= MAX_FAILED_REASON_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_FAILED_REASON_LENGTH);
    }
}
