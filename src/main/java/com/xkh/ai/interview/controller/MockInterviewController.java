package com.xkh.ai.interview.controller;

import com.xkh.ai.interview.agent.InterviewAssistantAgentService;
import com.xkh.ai.interview.orchestrator.InterviewAgentOrchestrator;
import com.xkh.ai.interview.service.dto.*;
import com.xkh.ai.interview.support.AiModelCallException;
import com.xkh.ai.interview.support.AiModelCallAuditQueryService;
import com.xkh.ai.interview.support.AiStructuredOutputException;
import com.xkh.ai.interview.support.AgentConversationAuditQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Controller
public class MockInterviewController {

    private static final Logger logger = LoggerFactory.getLogger(MockInterviewController.class);

    private final InterviewAgentOrchestrator interviewAgentOrchestrator;
    private final AiModelCallAuditQueryService auditQueryService;
    private final InterviewAssistantAgentService interviewAssistantAgentService;
    private final AgentConversationAuditQueryService agentConversationAuditQueryService;

    public MockInterviewController(InterviewAgentOrchestrator interviewAgentOrchestrator,
                                   AiModelCallAuditQueryService auditQueryService,
                                   InterviewAssistantAgentService interviewAssistantAgentService,
                                   AgentConversationAuditQueryService agentConversationAuditQueryService) {
        this.interviewAgentOrchestrator = interviewAgentOrchestrator;
        this.auditQueryService = auditQueryService;
        this.interviewAssistantAgentService = interviewAssistantAgentService;
        this.agentConversationAuditQueryService = agentConversationAuditQueryService;
    }

    /**
     * 首页
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * 上传简历页面
     */
    @GetMapping("/upload")
    public String uploadPage() {
        return "upload";
    }

    /**
     * Prompt 效果评估看板
     */
    @GetMapping("/audit/prompt-dashboard")
    public String promptDashboardPage() {
        return "prompt-dashboard";
    }

    /**
     * 上传简历文件并评分
     */
    @PostMapping("/api/resume/upload")
    @ResponseBody
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(interviewAgentOrchestrator.analyzeUploadedResume(file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (AiModelCallException | AiStructuredOutputException e) {
            return aiGatewayError(e);
        } catch (IOException e) {
            logger.error("上传简历失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败：" + e.getMessage()));
        } catch (Exception e) {
            logger.error("评分失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "评分失败：" + e.getMessage()));
        }
    }

    /**
     * 简历分析结果页面
     */
    @GetMapping("/analysis/{resumeId}")
    public String analysisPage(@PathVariable String resumeId, Model model) {
        ResumeData resumeData = interviewAgentOrchestrator.getResumeById(resumeId);
        if (resumeData == null) {
            return "redirect:/upload";
        }

        model.addAttribute("resumeId", resumeId);
        model.addAttribute("scoreResult", resumeData.getScoreResult());
        return "analysis";
    }

    /**
     * 获取简历分析详情
     */
    @GetMapping("/api/resume/{resumeId}/analysis")
    @ResponseBody
    public ResponseEntity<?> getResumeAnalysis(@PathVariable String resumeId) {
        ResumeData resumeData = interviewAgentOrchestrator.getResumeById(resumeId);
        if (resumeData == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resumeData.getScoreResult());
    }

    /**
     * 模拟面试页面
     */
    @GetMapping("/interview/{resumeId}")
    public String interviewPage(@PathVariable String resumeId, Model model) {
        ResumeData resumeData = interviewAgentOrchestrator.getResumeById(resumeId);
        if (resumeData == null) {
            return "redirect:/upload";
        }

        model.addAttribute("resumeId", resumeId);
        return "interview";
    }

    /**
     * 生成面试问题
     */
    @PostMapping("/api/interview/{resumeId}/questions")
    @ResponseBody
    public ResponseEntity<?> generateQuestions(@PathVariable String resumeId) {
        try {
            return ResponseEntity.ok(interviewAgentOrchestrator.generateInterviewQuestions(resumeId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (AiModelCallException | AiStructuredOutputException e) {
            return aiGatewayError(e);
        } catch (Exception e) {
            logger.error("生成问题失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "生成问题失败：" + e.getMessage()));
        }
    }

    /**
     * 基于目标岗位 JD 生成 RAG 增强面试问题
     */
    @PostMapping("/api/interview/{resumeId}/rag-questions")
    @ResponseBody
    public ResponseEntity<?> generateRagQuestions(
            @PathVariable String resumeId,
            @RequestBody JobDescriptionRequest request) {
        try {
            return ResponseEntity.ok(interviewAgentOrchestrator.generateRagInterviewQuestions(
                    resumeId,
                    request.getJobDescription(),
                    parseTopK(request.getTopK())
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (AiModelCallException | AiStructuredOutputException e) {
            return aiGatewayError(e);
        } catch (Exception e) {
            logger.error("生成 RAG 面试问题失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "生成 RAG 面试问题失败：" + e.getMessage()));
        }
    }

    /**
     * 提交答案并评估
     */
    @PostMapping("/api/interview/{resumeId}/submit")
    @ResponseBody
    public ResponseEntity<?> submitAnswers(
            @PathVariable String resumeId,
            @RequestBody Map<Integer, String> answers) {
        try {
            return ResponseEntity.ok(interviewAgentOrchestrator.evaluateAnswers(resumeId, answers));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (AiModelCallException | AiStructuredOutputException e) {
            return aiGatewayError(e);
        } catch (Exception e) {
            logger.error("评估答案失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "评估失败：" + e.getMessage()));
        }
    }

    /**
     * 查看评估结果页面
     */
    @GetMapping("/result/{resumeId}")
    public String resultPage(@PathVariable String resumeId, Model model) {
        ResumeData resumeData = interviewAgentOrchestrator.getResumeById(resumeId);
        if (resumeData == null) {
            return "redirect:/upload";
        }

        model.addAttribute("resumeId", resumeId);
        model.addAttribute("evaluation", resumeData.getEvaluation());
        model.addAttribute("questions", resumeData.getQuestions());
        return "result";
    }

    // ==================== RAG 向量检索接口 ====================

    /**
     * 手动触发简历向量化（用于历史数据补录）
     */
    @PostMapping("/api/rag/resume/{resumeId}/vectorize")
    @ResponseBody
    public ResponseEntity<?> vectorizeResume(@PathVariable String resumeId) {
        try {
            interviewAgentOrchestrator.vectorizeResume(resumeId);
            return ResponseEntity.ok(Map.of("success", true, "resumeId", resumeId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("向量化失败, resumeId={}", resumeId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "向量化失败：" + e.getMessage()));
        }
    }

    /**
     * 根据文本搜索相似简历（支持JD匹配、关键词搜索）
     */
    @PostMapping("/api/rag/search")
    @ResponseBody
    public ResponseEntity<?> searchResumesByText(@RequestBody Map<String, Object> request) {
        try {
            String queryText = (String) request.get("query");
            int topK = parseTopK(request.get("topK"));
            return ResponseEntity.ok(Map.of(
                    "query", queryText,
                    "results", interviewAgentOrchestrator.searchResumes(queryText, topK)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("文本检索失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "检索失败：" + e.getMessage()));
        }
    }

    /**
     * 分析候选人简历与目标岗位 JD 的匹配度
     */
    @PostMapping("/api/jd/{resumeId}/match")
    @ResponseBody
    public ResponseEntity<?> matchJobDescription(
            @PathVariable String resumeId,
            @RequestBody JobDescriptionRequest request) {
        try {
            return ResponseEntity.ok(interviewAgentOrchestrator.matchJobDescription(
                    resumeId,
                    request.getJobDescription()
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (AiModelCallException | AiStructuredOutputException e) {
            return aiGatewayError(e);
        } catch (Exception e) {
            logger.error("JD 匹配失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "JD 匹配失败：" + e.getMessage()));
        }
    }

    /**
     * ReAct Agent 对话入口，可自主调用简历画像、面试问题和相似简历检索工具
     */
    @PostMapping("/api/agent/interview-assistant/chat")
    @ResponseBody
    public ResponseEntity<?> chatWithInterviewAssistant(@RequestBody AgentChatRequest request) {
        try {
            return ResponseEntity.ok(interviewAssistantAgentService.chat(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Agent 对话失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Agent 对话失败：" + e.getMessage()));
        }
    }

    /**
     * 查询 Agent 对话消息审计记录
     */
    @GetMapping("/api/audit/agent-messages")
    @ResponseBody
    public ResponseEntity<?> listAgentConversationMessages(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String traceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(agentConversationAuditQueryService.listRecent(conversationId, traceId, limit));
    }

    /**
     * 查询最近的 AI 模型调用审计记录
     */
    @GetMapping("/api/audit/model-calls")
    @ResponseBody
    public ResponseEntity<?> listModelCallAudits(
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String operationName,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(auditQueryService.listRecent(traceId, operationName, limit));
    }

    /**
     * 查询 Prompt 版本维度的模型调用指标
     */
    @GetMapping("/api/audit/prompt-metrics")
    @ResponseBody
    public ResponseEntity<?> listPromptMetrics(
            @RequestParam(required = false) String operationName,
            @RequestParam(required = false) String promptVersion,
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(auditQueryService.listPromptMetrics(operationName, promptVersion, limit));
    }

    /**
     * 查询 Prompt 版本维度的失败原因分布
     */
    @GetMapping("/api/audit/failure-reasons")
    @ResponseBody
    public ResponseEntity<?> listFailureReasons(
            @RequestParam(required = false) String operationName,
            @RequestParam(required = false) String promptVersion,
            @RequestParam(defaultValue = "1000") int limit) {
        return ResponseEntity.ok(auditQueryService.listFailureReasons(operationName, promptVersion, limit));
    }

    private int parseTopK(Object topK) {
        if (topK instanceof Number number) {
            return Math.max(1, Math.min(number.intValue(), 20));
        }
        return 5;
    }

    private ResponseEntity<Map<String, String>> aiGatewayError(RuntimeException e) {
        logger.warn("AI gateway error", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
