package com.xkh.ai.interview.controller;

import com.xkh.ai.interview.service.MockInterviewService;
import com.xkh.ai.interview.service.dto.*;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@Controller
public class MockInterviewController {

    private static final Logger logger = LoggerFactory.getLogger(MockInterviewController.class);

    private final MockInterviewService interviewService;

    public MockInterviewController(MockInterviewService interviewService) {
        this.interviewService = interviewService;
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
            // 读取并返回纯文本
            String resumeText= reader.read().get(0).getText();

            // 调用 AI 服务进行评分
            ResumeScoreResult scoreResult = interviewService.scoreResume(resumeText);
            
            // 保存简历文本到会话（持久化到MySQL + 缓存到Redis）
            String resumeId = UUID.randomUUID().toString();
            String originalFileName = file.getOriginalFilename();
            interviewService.saveResume(resumeId, originalFileName, resumeText, scoreResult);
            
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
}
