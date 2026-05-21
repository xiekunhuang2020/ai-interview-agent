package com.xkh.ai.interview.service.workflow;

import com.xkh.ai.interview.service.agent.AnswerEvaluationAgent;
import com.xkh.ai.interview.service.agent.InterviewQuestionAgent;
import com.xkh.ai.interview.service.agent.JobDescriptionMatchAgent;
import com.xkh.ai.interview.service.agent.RagInterviewQuestionAgent;
import com.xkh.ai.interview.service.agent.ResumeAnalysisAgent;
import com.xkh.ai.interview.dto.InterviewEvaluationDTO;
import com.xkh.ai.interview.dto.InterviewQuestionsDTO;
import com.xkh.ai.interview.dto.InterviewSessionInfoDTO;
import com.xkh.ai.interview.dto.JobDescriptionMatchResultDTO;
import com.xkh.ai.interview.dto.ResumeDataDTO;
import com.xkh.ai.interview.dto.ResumeUploadResultDTO;
import com.xkh.ai.interview.service.tool.ResumeParseTool;
import com.xkh.ai.interview.service.tool.ResumeRepositoryTool;
import com.xkh.ai.interview.service.tool.ResumeVectorTool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class InterviewWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(InterviewWorkflowService.class);

    private final ResumeParseTool resumeParseTool;
    private final ResumeRepositoryTool resumeRepositoryTool;
    private final ResumeVectorTool resumeVectorTool;
    private final ResumeAnalysisAgent resumeAnalysisAgent;
    private final InterviewQuestionAgent interviewQuestionAgent;
    private final AnswerEvaluationAgent answerEvaluationAgent;
    private final JobDescriptionMatchAgent jobDescriptionMatchAgent;
    private final RagInterviewQuestionAgent ragInterviewQuestionAgent;
    private final InterviewSessionService interviewSessionService;

    /**
     * 注入简历解析、存储、向量化、模型任务和会话状态服务，组成完整面试工作流。
     */
    public InterviewWorkflowService(ResumeParseTool resumeParseTool,
                                    ResumeRepositoryTool resumeRepositoryTool,
                                    ResumeVectorTool resumeVectorTool,
                                    ResumeAnalysisAgent resumeAnalysisAgent,
                                    InterviewQuestionAgent interviewQuestionAgent,
                                    AnswerEvaluationAgent answerEvaluationAgent,
                                    JobDescriptionMatchAgent jobDescriptionMatchAgent,
                                    RagInterviewQuestionAgent ragInterviewQuestionAgent,
                                    InterviewSessionService interviewSessionService) {
        this.resumeParseTool = resumeParseTool;
        this.resumeRepositoryTool = resumeRepositoryTool;
        this.resumeVectorTool = resumeVectorTool;
        this.resumeAnalysisAgent = resumeAnalysisAgent;
        this.interviewQuestionAgent = interviewQuestionAgent;
        this.answerEvaluationAgent = answerEvaluationAgent;
        this.jobDescriptionMatchAgent = jobDescriptionMatchAgent;
        this.ragInterviewQuestionAgent = ragInterviewQuestionAgent;
        this.interviewSessionService = interviewSessionService;
    }

    /**
     * 上传简历后完成解析、AI 评分、数据库保存和向量库写入。
     */
    public ResumeUploadResultDTO analyzeUploadedResume(MultipartFile file) throws IOException {
        validateResumeFile(file);

        String originalFileName = file.getOriginalFilename();
        String resumeText = resumeParseTool.parse(file);
        if (StringUtils.isBlank(resumeText)) {
            throw new IllegalArgumentException("简历内容解析为空，请检查文件内容");
        }

        String resumeId = UUID.randomUUID().toString();
        interviewSessionService.markUploaded(resumeId, originalFileName);
        try {
            var scoreResult = resumeAnalysisAgent.analyze(resumeText);
            resumeRepositoryTool.saveAnalyzedResume(resumeId, originalFileName, resumeText, scoreResult);
            int vectorChunkCount = resumeVectorTool.addResume(resumeId, originalFileName, resumeText);
            logger.info("Resume vector indexing completed, resumeId={}, chunkCount={}", resumeId, vectorChunkCount);
            interviewSessionService.markAnalyzed(resumeId, originalFileName);
            return new ResumeUploadResultDTO(resumeId, scoreResult);
        } catch (RuntimeException e) {
            interviewSessionService.markFailed(resumeId, "RESUME_ANALYSIS", e);
            throw e;
        }
    }

    /**
     * 根据简历 ID 查询简历工作台所需的完整数据。
     */
    public ResumeDataDTO getResumeById(String resumeId) {
        return resumeRepositoryTool.findById(resumeId);
    }

    /**
     * 查询简历当前所在的面试流程状态。
     */
    public InterviewSessionInfoDTO getSessionInfo(String resumeId, ResumeDataDTO resumeData) {
        return interviewSessionService.findInfo(resumeId, resumeData);
    }

    /**
     * 基于简历内容生成普通面试题，并保存到当前简历记录。
     */
    public InterviewQuestionsDTO generateInterviewQuestions(String resumeId) {
        ResumeDataDTO resumeData = requireResume(resumeId);
        try {
            InterviewQuestionsDTO questions = interviewQuestionAgent.generate(resumeData.getResumeText());
            resumeRepositoryTool.saveQuestions(resumeId, questions);
            interviewSessionService.markQuestionsGenerated(resumeId);
            return questions;
        } catch (RuntimeException e) {
            interviewSessionService.markFailed(resumeId, "QUESTION_GENERATION", e);
            throw e;
        }
    }

    /**
     * 根据候选人答案进行 AI 复盘评分，并保存本次面试评估结果。
     */
    public InterviewEvaluationDTO evaluateAnswers(String resumeId, Map<Integer, String> answers) {
        ResumeDataDTO resumeData = requireResume(resumeId);
        if (resumeData.getQuestions() == null || resumeData.getQuestions().getQuestions() == null
                || resumeData.getQuestions().getQuestions().isEmpty()) {
            throw new IllegalStateException("请先生成面试问题，再提交答案");
        }

        interviewSessionService.markAnswerSubmitted(resumeId);
        try {
            InterviewEvaluationDTO evaluation = answerEvaluationAgent.evaluate(
                    resumeData.getResumeText(),
                    resumeData.getQuestions(),
                    answers
            );
            resumeRepositoryTool.saveEvaluation(resumeId, evaluation);
            interviewSessionService.markEvaluated(resumeId);
            return evaluation;
        } catch (RuntimeException e) {
            interviewSessionService.markFailed(resumeId, "ANSWER_EVALUATION", e);
            throw e;
        }
    }

    /**
     * 分析指定简历与目标岗位 JD 的匹配度，输出优势、缺口和建议。
     */
    public JobDescriptionMatchResultDTO matchJobDescription(String resumeId, String jobDescription) {
        if (StringUtils.isBlank(jobDescription)) {
            throw new IllegalArgumentException("jobDescription不能为空");
        }

        ResumeDataDTO resumeData = requireResume(resumeId);
        try {
            JobDescriptionMatchResultDTO matchResult = jobDescriptionMatchAgent.match(resumeData.getResumeText(), jobDescription);
            resumeRepositoryTool.saveJobMatch(resumeId, jobDescription, matchResult);
            interviewSessionService.markJobMatched(resumeId);
            return matchResult;
        } catch (RuntimeException e) {
            interviewSessionService.markFailed(resumeId, "JD_MATCH", e);
            throw e;
        }
    }

    /**
     * 结合目标岗位 JD 和相似简历召回结果，生成岗位增强面试题。
     */
    public InterviewQuestionsDTO generateRagInterviewQuestions(String resumeId, String jobDescription, int topK) {
        if (StringUtils.isBlank(jobDescription)) {
            throw new IllegalArgumentException("jobDescription不能为空");
        }

        ResumeDataDTO resumeData = requireResume(resumeId);
        try {
            InterviewQuestionsDTO questions = ragInterviewQuestionAgent.generate(
                    resumeData.getResumeText(),
                    jobDescription,
                    resumeId,
                    normalizeTopK(topK)
            );
            resumeRepositoryTool.saveQuestions(resumeId, questions);
            interviewSessionService.markQuestionsGenerated(resumeId);
            return questions;
        } catch (RuntimeException e) {
            interviewSessionService.markFailed(resumeId, "QUESTION_GENERATION", e);
            throw e;
        }
    }

    /**
     * 查询简历并统一处理不存在的情况，避免各流程重复判空。
     */
    private ResumeDataDTO requireResume(String resumeId) {
        ResumeDataDTO resumeData = resumeRepositoryTool.findById(resumeId);
        if (resumeData == null) {
            throw new NoSuchElementException("简历不存在：" + resumeId);
        }
        return resumeData;
    }

    /**
     * 校验上传文件是否为空，以及格式是否属于支持的简历类型。
     */
    private void validateResumeFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (!resumeParseTool.supports(file.getOriginalFilename())) {
            throw new IllegalArgumentException("仅支持 PDF、DOC、DOCX 或 TXT 格式的简历文件");
        }
    }

    /**
     * 规范向量检索数量，限制在 1 到 20 之间。
     */
    private int normalizeTopK(int topK) {
        return Math.max(1, Math.min(topK, 20));
    }

}

