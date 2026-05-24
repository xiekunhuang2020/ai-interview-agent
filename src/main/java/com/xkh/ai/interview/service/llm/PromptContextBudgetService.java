package com.xkh.ai.interview.service.llm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@ConfigurationProperties(prefix = "ai-interview.context-budget")
public class PromptContextBudgetService {

    private static final Pattern TRUNCATED_PATTERN = Pattern.compile("\\.\\.\\.\\[truncated, removed (\\d+) chars]");

    private boolean enabled = true;
    private int resumeMaxChars = 9000;
    private int jdMaxChars = 3500;
    private int answerMaxChars = 3000;
    private int assistantUserMessageMaxChars = 2000;
    private int ragDocumentMaxChars = 1000;
    private int ragTotalMaxChars = 4500;
    private int toolResumeSnippetMaxChars = 800;
    private int toolSearchSnippetMaxChars = 700;
    private int toolQuestionMaxChars = 500;

    /**
     * 按简历预算裁剪文本，避免完整长简历无限进入 Prompt。
     */
    public String limitResumeText(String text) {
        return limit(text, resumeMaxChars);
    }

    /**
     * 按岗位说明预算裁剪文本，控制 JD 输入长度。
     */
    public String limitJobDescription(String text) {
        return limit(text, jdMaxChars);
    }

    /**
     * 按回答预算裁剪候选人答案，避免单个长答案撑大评估 Prompt。
     */
    public String limitAnswer(String text) {
        return limit(text, answerMaxChars);
    }

    /**
     * 按 AI 顾问单轮输入预算裁剪用户问题。
     */
    public String limitAssistantUserMessage(String text) {
        return limit(text, assistantUserMessageMaxChars);
    }

    /**
     * 按工具画像预算裁剪简历片段。
     */
    public String limitToolResumeSnippet(String text) {
        return limit(text, toolResumeSnippetMaxChars);
    }

    /**
     * 按工具检索预算裁剪相似简历片段。
     */
    public String limitToolSearchSnippet(String text) {
        return limit(text, toolSearchSnippetMaxChars);
    }

    /**
     * 按工具问题预算裁剪面试题文本。
     */
    public String limitToolQuestion(String text) {
        return limit(text, toolQuestionMaxChars);
    }

    /**
     * 使用 Spring AI RAG DocumentPostProcessor 前置裁剪召回文档。
     */
    public List<Document> limitRagDocuments(List<Document> documents) {
        if (!enabled || documents == null || documents.isEmpty()) {
            return documents == null ? List.of() : documents;
        }
        List<Document> limitedDocuments = new ArrayList<>();
        int remainingChars = Math.max(0, ragTotalMaxChars);
        for (Document document : documents) {
            if (document == null || remainingChars <= 0) {
                break;
            }
            String limitedText = limit(document.getText(), Math.min(ragDocumentMaxChars, remainingChars));
            remainingChars -= limitedText.length();
            limitedDocuments.add(new Document(limitedText, document.getMetadata()));
        }
        return limitedDocuments;
    }

    /**
     * 根据最终发送给模型的消息统计 Prompt 字符数和裁剪字符数。
     */
    public ContextUsage contextUsageOf(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ContextUsage(0, 0, false);
        }
        int promptChars = 0;
        int clippedChars = 0;
        for (Message message : messages) {
            if (message == null || message.getText() == null) {
                continue;
            }
            String text = message.getText();
            promptChars += text.length();
            clippedChars += clippedCharsOf(text);
        }
        return new ContextUsage(promptChars, clippedChars, clippedChars > 0);
    }

    /**
     * 按字符预算裁剪文本，并保留可统计的裁剪标记。
     */
    private String limit(String text, int maxChars) {
        String safeText = StringUtils.defaultString(text);
        if (!enabled || maxChars <= 0 || safeText.length() <= maxChars) {
            return safeText;
        }
        int removedChars = safeText.length() - maxChars;
        return safeText.substring(0, maxChars) + "\n...[truncated, removed " + removedChars + " chars]";
    }

    /**
     * 从裁剪标记中统计被移除的字符数量。
     */
    private int clippedCharsOf(String text) {
        Matcher matcher = TRUNCATED_PATTERN.matcher(text);
        int clippedChars = 0;
        while (matcher.find()) {
            clippedChars += Integer.parseInt(matcher.group(1));
        }
        return clippedChars;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getResumeMaxChars() {
        return resumeMaxChars;
    }

    public void setResumeMaxChars(int resumeMaxChars) {
        this.resumeMaxChars = resumeMaxChars;
    }

    public int getJdMaxChars() {
        return jdMaxChars;
    }

    public void setJdMaxChars(int jdMaxChars) {
        this.jdMaxChars = jdMaxChars;
    }

    public int getAnswerMaxChars() {
        return answerMaxChars;
    }

    public void setAnswerMaxChars(int answerMaxChars) {
        this.answerMaxChars = answerMaxChars;
    }

    public int getAssistantUserMessageMaxChars() {
        return assistantUserMessageMaxChars;
    }

    public void setAssistantUserMessageMaxChars(int assistantUserMessageMaxChars) {
        this.assistantUserMessageMaxChars = assistantUserMessageMaxChars;
    }

    public int getRagDocumentMaxChars() {
        return ragDocumentMaxChars;
    }

    public void setRagDocumentMaxChars(int ragDocumentMaxChars) {
        this.ragDocumentMaxChars = ragDocumentMaxChars;
    }

    public int getRagTotalMaxChars() {
        return ragTotalMaxChars;
    }

    public void setRagTotalMaxChars(int ragTotalMaxChars) {
        this.ragTotalMaxChars = ragTotalMaxChars;
    }

    public int getToolResumeSnippetMaxChars() {
        return toolResumeSnippetMaxChars;
    }

    public void setToolResumeSnippetMaxChars(int toolResumeSnippetMaxChars) {
        this.toolResumeSnippetMaxChars = toolResumeSnippetMaxChars;
    }

    public int getToolSearchSnippetMaxChars() {
        return toolSearchSnippetMaxChars;
    }

    public void setToolSearchSnippetMaxChars(int toolSearchSnippetMaxChars) {
        this.toolSearchSnippetMaxChars = toolSearchSnippetMaxChars;
    }

    public int getToolQuestionMaxChars() {
        return toolQuestionMaxChars;
    }

    public void setToolQuestionMaxChars(int toolQuestionMaxChars) {
        this.toolQuestionMaxChars = toolQuestionMaxChars;
    }

    public record ContextUsage(Integer promptChars, Integer clippedChars, boolean clipped) {
    }
}
