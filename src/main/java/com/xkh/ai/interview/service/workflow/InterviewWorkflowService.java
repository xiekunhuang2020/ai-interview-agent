package com.xkh.ai.interview.service.workflow;

import com.xkh.ai.interview.service.agent.AnswerEvaluationAgent;
import com.xkh.ai.interview.service.agent.InterviewQuestionAgent;
import com.xkh.ai.interview.service.agent.JobDescriptionMatchAgent;
import com.xkh.ai.interview.service.agent.RagInterviewQuestionAgent;
import com.xkh.ai.interview.service.agent.ResumeAnalysisAgent;
import com.xkh.ai.interview.dto.InterviewEvaluation;
import com.xkh.ai.interview.dto.InterviewQuestions;
import com.xkh.ai.interview.dto.JobDescriptionMatchResult;
import com.xkh.ai.interview.dto.ResumeData;
import com.xkh.ai.interview.dto.ResumeSearchResult;
import com.xkh.ai.interview.dto.ResumeUploadResult;
import com.xkh.ai.interview.service.tool.ResumeParseTool;
import com.xkh.ai.interview.service.tool.ResumeRepositoryTool;
import com.xkh.ai.interview.service.tool.ResumeVectorTool;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class InterviewWorkflowService {

    private final ResumeParseTool resumeParseTool;
    private final ResumeRepositoryTool resumeRepositoryTool;
    private final ResumeVectorTool resumeVectorTool;
    private final ResumeAnalysisAgent resumeAnalysisAgent;
    private final InterviewQuestionAgent interviewQuestionAgent;
    private final AnswerEvaluationAgent answerEvaluationAgent;
    private final JobDescriptionMatchAgent jobDescriptionMatchAgent;
    private final RagInterviewQuestionAgent ragInterviewQuestionAgent;

    public InterviewWorkflowService(ResumeParseTool resumeParseTool,
                                    ResumeRepositoryTool resumeRepositoryTool,
                                    ResumeVectorTool resumeVectorTool,
                                    ResumeAnalysisAgent resumeAnalysisAgent,
                                    InterviewQuestionAgent interviewQuestionAgent,
                                    AnswerEvaluationAgent answerEvaluationAgent,
                                    JobDescriptionMatchAgent jobDescriptionMatchAgent,
                                    RagInterviewQuestionAgent ragInterviewQuestionAgent) {
        this.resumeParseTool = resumeParseTool;
        this.resumeRepositoryTool = resumeRepositoryTool;
        this.resumeVectorTool = resumeVectorTool;
        this.resumeAnalysisAgent = resumeAnalysisAgent;
        this.interviewQuestionAgent = interviewQuestionAgent;
        this.answerEvaluationAgent = answerEvaluationAgent;
        this.jobDescriptionMatchAgent = jobDescriptionMatchAgent;
        this.ragInterviewQuestionAgent = ragInterviewQuestionAgent;
    }

    public ResumeUploadResult analyzeUploadedResume(MultipartFile file) throws IOException {
        validateResumeFile(file);

        String originalFileName = file.getOriginalFilename();
        String resumeText = resumeParseTool.parse(file);
        if (StringUtils.isBlank(resumeText)) {
            throw new IllegalArgumentException("简历内容解析为空，请检查文件内容");
        }

        String resumeId = UUID.randomUUID().toString();
        var scoreResult = resumeAnalysisAgent.analyze(resumeText);
        resumeRepositoryTool.saveAnalyzedResume(resumeId, originalFileName, resumeText, scoreResult);
        resumeVectorTool.addResume(resumeId, originalFileName, resumeText);

        return new ResumeUploadResult(resumeId, scoreResult);
    }

    public ResumeData getResumeById(String resumeId) {
        return resumeRepositoryTool.findById(resumeId);
    }

    public InterviewQuestions generateInterviewQuestions(String resumeId) {
        ResumeData resumeData = requireResume(resumeId);
        InterviewQuestions questions = interviewQuestionAgent.generate(resumeData.getResumeText());
        resumeRepositoryTool.saveQuestions(resumeId, questions);
        return questions;
    }

    public InterviewEvaluation evaluateAnswers(String resumeId, Map<Integer, String> answers) {
        ResumeData resumeData = requireResume(resumeId);
        if (resumeData.getQuestions() == null || resumeData.getQuestions().getQuestions() == null
                || resumeData.getQuestions().getQuestions().isEmpty()) {
            throw new IllegalStateException("请先生成面试问题，再提交答案");
        }

        InterviewEvaluation evaluation = answerEvaluationAgent.evaluate(
                resumeData.getResumeText(),
                resumeData.getQuestions(),
                answers
        );
        resumeRepositoryTool.saveEvaluation(resumeId, evaluation);
        return evaluation;
    }

    public void vectorizeResume(String resumeId) {
        ResumeData resumeData = requireResume(resumeId);
        resumeVectorTool.addResume(resumeId, "", resumeData.getResumeText());
    }

    public List<ResumeSearchResult> searchResumes(String queryText, int topK) {
        if (StringUtils.isBlank(queryText)) {
            throw new IllegalArgumentException("query参数不能为空");
        }
        return resumeVectorTool.search(queryText, normalizeTopK(topK)).stream()
                .map(this::toSearchResult)
                .toList();
    }

    public JobDescriptionMatchResult matchJobDescription(String resumeId, String jobDescription) {
        if (StringUtils.isBlank(jobDescription)) {
            throw new IllegalArgumentException("jobDescription不能为空");
        }

        ResumeData resumeData = requireResume(resumeId);
        return jobDescriptionMatchAgent.match(resumeData.getResumeText(), jobDescription);
    }

    public InterviewQuestions generateRagInterviewQuestions(String resumeId, String jobDescription, int topK) {
        if (StringUtils.isBlank(jobDescription)) {
            throw new IllegalArgumentException("jobDescription不能为空");
        }

        ResumeData resumeData = requireResume(resumeId);
        InterviewQuestions questions = ragInterviewQuestionAgent.generate(
                resumeData.getResumeText(),
                jobDescription,
                resumeId,
                normalizeTopK(topK)
        );
        resumeRepositoryTool.saveQuestions(resumeId, questions);
        return questions;
    }

    private ResumeData requireResume(String resumeId) {
        ResumeData resumeData = resumeRepositoryTool.findById(resumeId);
        if (resumeData == null) {
            throw new NoSuchElementException("简历不存在：" + resumeId);
        }
        return resumeData;
    }

    private void validateResumeFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (!resumeParseTool.supports(file.getOriginalFilename())) {
            throw new IllegalArgumentException("仅支持 PDF、DOC、DOCX 或 TXT 格式的简历文件");
        }
    }

    private int normalizeTopK(int topK) {
        return Math.max(1, Math.min(topK, 20));
    }

    private ResumeSearchResult toSearchResult(Document doc) {
        Object resumeId = doc.getMetadata().get("resumeId");
        Object fileName = doc.getMetadata().get("fileName");
        return new ResumeSearchResult(
                resumeId == null ? "" : resumeId.toString(),
                fileName == null ? "" : fileName.toString(),
                doc.getText()
        );
    }
}
