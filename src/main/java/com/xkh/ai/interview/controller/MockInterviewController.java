package com.xkh.ai.interview.controller;

import com.xkh.ai.interview.service.MockInterviewService;
import com.xkh.ai.interview.service.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
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

    private final MockInterviewService interviewService;
    private final VectorStore vectorStore;

    public MockInterviewController(MockInterviewService interviewService, VectorStore vectorStore) {
        this.interviewService = interviewService;
        this.vectorStore = vectorStore;
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
     * 上传简历文件并评分
     */
    @PostMapping("/api/resume/upload")
    @ResponseBody
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
            }

            // 检查文件类型
            String fileName = file.getOriginalFilename();
            if (fileName == null || (!fileName.endsWith(".pdf") && !fileName.endsWith(".doc") &&
                    !fileName.endsWith(".docx") && !fileName.endsWith(".txt"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "仅支持 PDF、DOC、DOCX 或 TXT 格式的简历文件"));
            }

            // 读取文件内容
            TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
            String resumeText = reader.read().get(0).getText();

            // 调用 AI 服务进行评分
            ResumeScoreResult scoreResult = interviewService.scoreResume(resumeText);

            // 保存简历文本到会话（持久化到MySQL + 缓存到Redis + 向量库）
            String resumeId = UUID.randomUUID().toString();
            String originalFileName = file.getOriginalFilename();
            interviewService.saveResume(resumeId, originalFileName, resumeText, scoreResult);

            // 写入Spring AI Milvus Vector Store（RAG简历知识库）
            Document doc = new Document(resumeText, Map.of("resumeId", resumeId, "fileName", originalFileName));
            vectorStore.add(List.of(doc));
            logger.info("Resume vectorized and stored, resumeId={}", resumeId);

            Map<String, Object> response = new HashMap<>();
            response.put("resumeId", resumeId);
            response.put("scoreResult", scoreResult);

            return ResponseEntity.ok(response);
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
        ResumeData resumeData = interviewService.getResumeById(resumeId);
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
        ResumeData resumeData = interviewService.getResumeById(resumeId);
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
        ResumeData resumeData = interviewService.getResumeById(resumeId);
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
            ResumeData resumeData = interviewService.getResumeById(resumeId);
            if (resumeData == null) {
                return ResponseEntity.notFound().build();
            }

            InterviewQuestions questions = interviewService.generateInterviewQuestions(resumeData.getResumeText());
            interviewService.saveQuestions(resumeId, questions);

            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            logger.error("生成问题失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "生成问题失败：" + e.getMessage()));
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
            ResumeData resumeData = interviewService.getResumeById(resumeId);
            if (resumeData == null) {
                return ResponseEntity.notFound().build();
            }

            InterviewEvaluation evaluation = interviewService.evaluateAnswers(
                resumeData.getResumeText(),
                resumeData.getQuestions(),
                answers
            );
            interviewService.saveEvaluation(resumeId, evaluation);

            return ResponseEntity.ok(evaluation);
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
        ResumeData resumeData = interviewService.getResumeById(resumeId);
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
            ResumeData resumeData = interviewService.getResumeById(resumeId);
            if (resumeData == null) {
                return ResponseEntity.notFound().build();
            }
            Document doc = new Document(resumeData.getResumeText(), Map.of("resumeId", resumeId));
            vectorStore.add(List.of(doc));
            return ResponseEntity.ok(Map.of("success", true, "resumeId", resumeId));
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
            int topK = request.get("topK") != null ? (int) request.get("topK") : 5;
            if (queryText == null || queryText.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "query参数不能为空"));
            }

            List<Document> docs = vectorStore.similaritySearch(queryText);
            List<Map<String, Object>> results = new ArrayList<>();
            int count = 0;
            for (Document doc : docs) {
                if (count++ >= topK) break;
                Map<String, Object> item = new HashMap<>();
                item.put("resumeId", doc.getMetadata().get("resumeId"));
                item.put("fileName", doc.getMetadata().get("fileName"));
                item.put("resumeText", doc.getText());
                results.add(item);
            }
            return ResponseEntity.ok(Map.of("query", queryText, "results", results));
        } catch (Exception e) {
            logger.error("文本检索失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "检索失败：" + e.getMessage()));
        }
    }
}
