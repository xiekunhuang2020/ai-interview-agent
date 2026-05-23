package com.xkh.ai.interview.controller;

import com.xkh.ai.interview.dto.AgentChatRequestDTO;
import com.xkh.ai.interview.dto.JobDescriptionRequestDTO;
import com.xkh.ai.interview.dto.ResumeDataDTO;
import com.xkh.ai.interview.service.agent.InterviewAssistantAgentService;
import com.xkh.ai.interview.service.audit.AgentConversationAuditQueryService;
import com.xkh.ai.interview.service.audit.AiModelCallAuditQueryService;
import com.xkh.ai.interview.service.llm.AiModelCallException;
import com.xkh.ai.interview.service.llm.AiStructuredOutputException;
import com.xkh.ai.interview.service.rag.RagRecallEvaluationService;
import com.xkh.ai.interview.service.workflow.InterviewWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 负责处理前端 AJAX/SSE 调用，只返回 JSON 或流式响应。
 */
@RestController
public class InterviewApiController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewApiController.class);

    private final InterviewWorkflowService interviewWorkflowService;
    private final AiModelCallAuditQueryService auditQueryService;
    private final InterviewAssistantAgentService interviewAssistantAgentService;
    private final AgentConversationAuditQueryService agentConversationAuditQueryService;
    private final RagRecallEvaluationService ragRecallEvaluationService;

    /**
     * 注入简历工作流、AI 顾问、审计查询和 RAG 评估服务。
     */
    public InterviewApiController(InterviewWorkflowService interviewWorkflowService,
                                  AiModelCallAuditQueryService auditQueryService,
                                  InterviewAssistantAgentService interviewAssistantAgentService,
                                  AgentConversationAuditQueryService agentConversationAuditQueryService,
                                  RagRecallEvaluationService ragRecallEvaluationService) {
        this.interviewWorkflowService = interviewWorkflowService;
        this.auditQueryService = auditQueryService;
        this.interviewAssistantAgentService = interviewAssistantAgentService;
        this.agentConversationAuditQueryService = agentConversationAuditQueryService;
        this.ragRecallEvaluationService = ragRecallEvaluationService;
    }

    /**
     * 上传简历文件，完成解析、AI 评分、保存和向量化。
     */
    @PostMapping("/api/resume/upload")
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(interviewWorkflowService.analyzeUploadedResume(file));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (AiModelCallException | AiStructuredOutputException e) {
            return aiGatewayError(e);
        } catch (IOException e) {
            return serverError("上传简历失败", e, "上传失败：" + e.getMessage());
        } catch (Exception e) {
            return serverError("评分失败", e, "评分失败：" + e.getMessage());
        }
    }

    /**
     * 查询当前简历工作台数据，包括评分、面试题和评估结果。
     */
    @GetMapping("/api/resume/{resumeId}")
    public ResponseEntity<?> getResumeWorkspace(@PathVariable String resumeId) {
        ResumeDataDTO resumeData = interviewWorkflowService.getResumeById(resumeId);
        if (resumeData == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("resumeId", resumeData.getResumeId());
        response.put("scoreResult", resumeData.getScoreResult());
        response.put("questions", resumeData.getQuestions());
        response.put("evaluation", resumeData.getEvaluation());
        response.put("matchResult", resumeData.getMatchResult());
        response.put("jobDescription", resumeData.getJobDescription());
        response.put("session", interviewWorkflowService.getSessionInfo(resumeId, resumeData));
        return ResponseEntity.ok(response);
    }

    /**
     * 基于简历内容生成通用面试题。
     */
    @PostMapping("/api/interview/{resumeId}/questions")
    public ResponseEntity<?> generateQuestions(@PathVariable String resumeId) {
        try {
            return ResponseEntity.ok(interviewWorkflowService.generateInterviewQuestions(resumeId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (AiModelCallException | AiStructuredOutputException e) {
            return aiGatewayError(e);
        } catch (Exception e) {
            return serverError("生成问题失败", e, "生成问题失败：" + e.getMessage());
        }
    }

    /**
     * 结合目标 JD 和 RAG 检索上下文生成岗位定制面试题。
     */
    @PostMapping("/api/interview/{resumeId}/rag-questions")
    public ResponseEntity<?> generateRagQuestions(
            @PathVariable String resumeId,
            @RequestBody JobDescriptionRequestDTO request) {
        try {
            return ResponseEntity.ok(interviewWorkflowService.generateRagInterviewQuestions(
                    resumeId,
                    request.getJobDescription(),
                    parseTopK(request.getTopK())
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (AiModelCallException | AiStructuredOutputException e) {
            return aiGatewayError(e);
        } catch (Exception e) {
            return serverError("生成岗位定制面试题失败", e, "生成岗位定制面试题失败：" + e.getMessage());
        }
    }

    /**
     * 提交候选人答案，并调用 AI 生成面试复盘评估。
     */
    @PostMapping("/api/interview/{resumeId}/submit")
    public ResponseEntity<?> submitAnswers(
            @PathVariable String resumeId,
            @RequestBody Map<Integer, String> answers) {
        try {
            return ResponseEntity.ok(interviewWorkflowService.evaluateAnswers(resumeId, answers));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return badRequest(e);
        } catch (AiModelCallException | AiStructuredOutputException e) {
            return aiGatewayError(e);
        } catch (Exception e) {
            return serverError("评估答案失败", e, "评估失败：" + e.getMessage());
        }
    }

    /**
     * 分析候选人简历与目标岗位 JD 的匹配度。
     */
    @PostMapping("/api/jd/{resumeId}/match")
    public ResponseEntity<?> matchJobDescription(
            @PathVariable String resumeId,
            @RequestBody JobDescriptionRequestDTO request) {
        try {
            return ResponseEntity.ok(interviewWorkflowService.matchJobDescription(
                    resumeId,
                    request.getJobDescription()
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (AiModelCallException | AiStructuredOutputException e) {
            return aiGatewayError(e);
        } catch (Exception e) {
            return serverError("JD 匹配失败", e, "JD 匹配失败：" + e.getMessage());
        }
    }

    /**
     * AI 求职顾问 EventSource 流式对话入口。
     */
    @GetMapping(value = "/api/agent/interview-assistant/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<Map<String, Object>>>> streamWithInterviewAssistantByEventSource(
            @RequestParam String message,
            @RequestParam(required = false) String conversationId) {
        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage(message);
        request.setConversationId(conversationId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(interviewAssistantAgentService.stream(request));
    }

    /**
     * 查询 Agent 对话消息审计记录。
     */
    @GetMapping("/api/audit/agent-messages")
    public ResponseEntity<?> listAgentConversationMessages(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String traceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(agentConversationAuditQueryService.listRecent(conversationId, traceId, limit));
    }

    /**
     * 查询最近的 AI 模型调用审计记录。
     */
    @GetMapping("/api/audit/model-calls")
    public ResponseEntity<?> listModelCallAudits(
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String operationName,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(auditQueryService.listRecent(traceId, operationName, limit));
    }

    /**
     * 查询 Prompt 版本维度的模型调用指标。
     */
    @GetMapping("/api/audit/prompt-metrics")
    public ResponseEntity<?> listPromptMetrics(
            @RequestParam(required = false) String operationName,
            @RequestParam(required = false) String promptVersion,
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(auditQueryService.listPromptMetrics(operationName, promptVersion, limit));
    }

    /**
     * 查询 Prompt 版本维度的失败原因分布。
     */
    @GetMapping("/api/audit/failure-reasons")
    public ResponseEntity<?> listFailureReasons(
            @RequestParam(required = false) String operationName,
            @RequestParam(required = false) String promptVersion,
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(auditQueryService.listFailureReasons(operationName, promptVersion, limit));
    }

    /**
     * 运行本地 RAG 召回评估集，输出 TopK 命中率、平均耗时和未命中样例。
     */
    @GetMapping("/api/evaluation/rag-recall")
    public ResponseEntity<?> evaluateRagRecall(@RequestParam(defaultValue = "5") int topK) {
        try {
            return ResponseEntity.ok(ragRecallEvaluationService.evaluate(topK));
        } catch (IllegalStateException e) {
            return badRequest(e);
        } catch (Exception e) {
            return serverError("RAG 召回评估失败", e, "RAG 召回评估失败：" + e.getMessage());
        }
    }

    /**
     * 解析前端传入的 RAG 召回数量，未传时使用默认值。
     */
    private int parseTopK(Object topK) {
        if (topK instanceof Number number) {
            return number.intValue();
        }
        return 5;
    }

    /**
     * 统一返回 400 参数错误，避免每个接口重复组装响应体。
     */
    private ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 统一记录服务端异常日志，并返回给前端可读的错误文案。
     */
    private ResponseEntity<Map<String, String>> serverError(String logMessage, Exception e, String clientMessage) {
        logger.error(logMessage, e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, clientMessage);
    }

    /**
     * 统一处理模型调用或结构化输出失败，返回网关类错误状态。
     */
    private ResponseEntity<Map<String, String>> aiGatewayError(RuntimeException e) {
        logger.warn("AI gateway error", e);
        return error(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /**
     * 按指定 HTTP 状态码组装标准错误响应。
     */
    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}

