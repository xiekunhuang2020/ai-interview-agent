package com.xkh.ai.interview.service.llm;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Service
@ConfigurationProperties(prefix = "ai-interview.context-budget")
public class PromptContextBudgetService {

    private static final Pattern TRUNCATED_PATTERN = Pattern.compile("\\.\\.\\.\\[truncated, removed (\\d+) chars]");
    private static final int TRUNCATED_MARKER_TOKEN_RESERVE = 24;

    /**
     * 是否启用上下文预算控制，关闭后仅保留原文。
     */
    private boolean enabled = true;
    /**
     * 单次简历全文进入 Prompt 的最大估算 Token 数。
     */
    private int resumeMaxTokens = 2800;
    /**
     * 单次岗位 JD 进入 Prompt 的最大估算 Token 数。
     */
    private int jdMaxTokens = 1200;
    /**
     * 候选人单个回答进入评估 Prompt 的最大估算 Token 数。
     */
    private int answerMaxTokens = 1000;
    /**
     * AI 顾问单轮用户输入的最大估算 Token 数。
     */
    private int assistantUserMessageMaxTokens = 700;
    /**
     * RAG 单个召回文档进入 Prompt 的最大估算 Token 数。
     */
    private int ragDocumentMaxTokens = 350;
    /**
     * RAG 全部召回上下文进入 Prompt 的最大估算 Token 数。
     */
    private int ragTotalMaxTokens = 1400;
    /**
     * 工具返回简历画像片段的最大估算 Token 数。
     */
    private int toolResumeSnippetMaxTokens = 260;
    /**
     * 工具返回相似简历片段的最大估算 Token 数。
     */
    private int toolSearchSnippetMaxTokens = 230;
    /**
     * 工具返回单道面试题文本的最大估算 Token 数。
     */
    private int toolQuestionMaxTokens = 160;
    /**
     * 每类模型调用的目标输入 Token 预算，用于事后判断真实 usage 是否超预算。
     */
    private Map<String, Integer> operationInputTokenBudgets = defaultOperationInputTokenBudgets();
    /**
     * Spring AI 官方 Token 估算器，用于把字符预算升级为 Token 预算。
     */
    private final TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

    /**
     * 按简历 Token 预算裁剪文本，避免完整长简历无限进入 Prompt。
     */
    public String limitResumeText(String text) {
        return limitHeadAndTail(text, resumeMaxTokens);
    }

    /**
     * 按岗位说明 Token 预算裁剪文本，控制 JD 输入长度。
     */
    public String limitJobDescription(String text) {
        return limit(text, jdMaxTokens);
    }

    /**
     * 按回答 Token 预算裁剪候选人答案，避免单个长答案撑大评估 Prompt。
     */
    public String limitAnswer(String text) {
        return limit(text, answerMaxTokens);
    }

    /**
     * 按 AI 顾问单轮输入 Token 预算裁剪用户问题。
     */
    public String limitAssistantUserMessage(String text) {
        return limit(text, assistantUserMessageMaxTokens);
    }

    /**
     * 按工具画像 Token 预算裁剪简历片段。
     */
    public String limitToolResumeSnippet(String text) {
        return limit(text, toolResumeSnippetMaxTokens);
    }

    /**
     * 按工具检索 Token 预算裁剪相似简历片段。
     */
    public String limitToolSearchSnippet(String text) {
        return limit(text, toolSearchSnippetMaxTokens);
    }

    /**
     * 按工具问题 Token 预算裁剪面试题文本。
     */
    public String limitToolQuestion(String text) {
        return limit(text, toolQuestionMaxTokens);
    }

    /**
     * 使用 Spring AI RAG DocumentPostProcessor 前置裁剪召回文档。
     */
    public List<Document> limitRagDocuments(List<Document> documents) {
        if (!enabled || documents == null || documents.isEmpty()) {
            return documents == null ? List.of() : documents;
        }
        List<Document> limitedDocuments = new ArrayList<>();
        int remainingTokens = Math.max(0, ragTotalMaxTokens);
        for (Document document : documents) {
            if (document == null || remainingTokens <= 0) {
                break;
            }
            String limitedText = limit(document.getText(), Math.min(ragDocumentMaxTokens, remainingTokens));
            remainingTokens -= estimateTokens(limitedText);
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
     * 查询某个模型调用场景的目标输入 Token 预算，未配置时返回空。
     */
    public Integer inputTokenBudgetOf(String operationName) {
        if (StringUtils.isBlank(operationName) || operationInputTokenBudgets == null) {
            return null;
        }
        return operationInputTokenBudgets.get(operationName);
    }

    /**
     * 按 Token 预算裁剪文本，并保留可统计的裁剪标记。
     */
    private String limit(String text, int maxTokens) {
        String safeText = StringUtils.defaultString(text);
        if (!enabled || maxTokens <= 0 || estimateTokens(safeText) <= maxTokens) {
            return safeText;
        }
        int contentTokenBudget = Math.max(1, maxTokens - TRUNCATED_MARKER_TOKEN_RESERVE);
        int endIndex = findMaxEndIndex(safeText, contentTokenBudget);
        int removedChars = safeText.length() - endIndex;
        return StringUtils.stripEnd(safeText.substring(0, endIndex), null)
                + "\n...[truncated, removed " + removedChars + " chars]";
    }

    /**
     * 简历超预算时保留开头和结尾，降低项目经历或教育经历在后半段被整段丢失的概率。
     */
    private String limitHeadAndTail(String text, int maxTokens) {
        String safeText = StringUtils.defaultString(text);
        if (!enabled || maxTokens <= 0 || estimateTokens(safeText) <= maxTokens) {
            return safeText;
        }
        int contentTokenBudget = Math.max(2, maxTokens - TRUNCATED_MARKER_TOKEN_RESERVE);
        int headTokenBudget = Math.max(1, (int) Math.ceil(contentTokenBudget * 0.65D));
        int tailTokenBudget = Math.max(1, contentTokenBudget - headTokenBudget);
        int headEndIndex = findMaxEndIndex(safeText, headTokenBudget);
        int tailStartIndex = findMinStartIndex(safeText, tailTokenBudget);
        if (headEndIndex >= tailStartIndex) {
            return limit(safeText, maxTokens);
        }

        String head = StringUtils.stripEnd(safeText.substring(0, headEndIndex), null);
        String tail = StringUtils.stripStart(safeText.substring(tailStartIndex), null);
        int removedChars = tailStartIndex - headEndIndex;
        return head + "\n...[truncated, removed " + removedChars + " chars]\n" + tail;
    }

    /**
     * 使用 Spring AI 官方 TokenCountEstimator 估算文本 Token 数。
     */
    private int estimateTokens(String text) {
        return tokenCountEstimator.estimate(StringUtils.defaultString(text));
    }

    /**
     * 二分查找指定 Token 预算内能保留的最大字符位置。
     */
    private int findMaxEndIndex(String text, int maxTokens) {
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (estimateTokens(text.substring(0, mid)) <= maxTokens) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * 二分查找指定 Token 预算内能保留的最早结尾片段起点。
     */
    private int findMinStartIndex(String text, int maxTokens) {
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high) / 2;
            if (estimateTokens(text.substring(mid)) <= maxTokens) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
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

    /**
     * 初始化各场景的输入 Token 目标预算，后续可通过配置覆盖。
     */
    private Map<String, Integer> defaultOperationInputTokenBudgets() {
        Map<String, Integer> budgets = new LinkedHashMap<>();
        budgets.put("resume-analysis", 3000);
        budgets.put("jd-match", 3500);
        budgets.put("interview-question-generation", 4000);
        budgets.put("rag-interview-question-generation", 4000);
        budgets.put("answer-evaluation", 4000);
        budgets.put("interview-assistant-stream", 2500);
        budgets.put("interview-assistant-query-rewrite", 1000);
        budgets.put("interview-assistant-summary", 1800);
        budgets.put("jd-image-ocr", 1800);
        return budgets;
    }

    public record ContextUsage(Integer promptChars, Integer clippedChars, boolean clipped) {
    }
}
