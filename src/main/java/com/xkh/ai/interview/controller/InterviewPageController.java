package com.xkh.ai.interview.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 负责返回 Thymeleaf 页面，不处理具体业务逻辑。
 */
@Controller
public class InterviewPageController {

    /**
     * 打开产品首页。
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * 打开简历上传页面。
     */
    @GetMapping("/upload")
    public String uploadPage() {
        return "upload";
    }

    /**
     * 打开 Prompt 和模型调用效果看板。
     */
    @GetMapping("/audit/prompt-dashboard")
    public String promptDashboardPage() {
        return "prompt-dashboard";
    }

    /**
     * 打开简历分析结果页面，并把 resumeId 传给前端。
     */
    @GetMapping("/analysis/{resumeId}")
    public String analysisPage(@PathVariable String resumeId, Model model) {
        model.addAttribute("resumeId", resumeId);
        return "analysis";
    }

    /**
     * 打开岗位匹配页面，并把 resumeId 传给前端。
     */
    @GetMapping("/match/{resumeId}")
    public String matchPage(@PathVariable String resumeId, Model model) {
        model.addAttribute("resumeId", resumeId);
        return "match";
    }

    /**
     * 打开模拟面试页面，并把 resumeId 传给前端。
     */
    @GetMapping("/interview/{resumeId}")
    public String interviewPage(@PathVariable String resumeId, Model model) {
        model.addAttribute("resumeId", resumeId);
        return "interview";
    }

    /**
     * 打开面试评估结果页面，并把 resumeId 传给前端。
     */
    @GetMapping("/result/{resumeId}")
    public String resultPage(@PathVariable String resumeId, Model model) {
        model.addAttribute("resumeId", resumeId);
        return "result";
    }

    /**
     * 打开 AI 求职顾问页面，没有 resumeId 时也允许单独咨询。
     */
    @GetMapping({"/assistant", "/assistant/{resumeId}"})
    public String assistantPage(@PathVariable(required = false) String resumeId, Model model) {
        model.addAttribute("resumeId", resumeId == null ? "" : resumeId);
        return "assistant";
    }
}
