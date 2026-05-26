package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptMetricsResultDTO {
    /**
     * 模型调用场景，例如简历诊断、岗位匹配、AI 顾问流式对话。
     */
    private String operationName;

    /**
     * 当前聚合分组对应的 Prompt 版本。
     */
    private String promptVersion;

    /**
     * 当前分组中出现过的模型名称，多个模型用斜杠拼接。
     */
    private String modelNames;

    /**
     * 当前分组内的模型调用总次数。
     */
    private Long totalCalls;

    /**
     * 当前分组内调用成功的次数。
     */
    private Long successCalls;

    /**
     * 当前分组内调用失败的次数。
     */
    private Long failedCalls;

    /**
     * 成功调用占比，单位是百分比。
     */
    private Double successRate;

    /**
     * 平均模型调用耗时，单位毫秒。
     */
    private Double avgLatencyMs;

    /**
     * 最大模型调用耗时，单位毫秒。
     */
    private Long maxLatencyMs;

    /**
     * 有官方 Token 用量数据的调用次数。
     */
    private Long tokenSampleCalls;

    /**
     * 当前分组累计输入 Token 数。
     */
    private Long totalInputTokens;

    /**
     * 当前分组累计输出 Token 数。
     */
    private Long totalOutputTokens;

    /**
     * 当前分组累计总 Token 数。
     */
    private Long totalTokens;

    /**
     * 有 Token 样本的平均总 Token 数。
     */
    private Double avgTotalTokens;

    /**
     * 有 Prompt 字符数统计的调用次数。
     */
    private Long contextSampleCalls;

    /**
     * 发生上下文裁剪的调用次数。
     */
    private Long clippedCalls;

    /**
     * 当前分组累计 Prompt 字符数。
     */
    private Long totalPromptChars;

    /**
     * 当前分组累计被裁剪字符数。
     */
    private Long totalClippedChars;

    /**
     * 有上下文样本的平均 Prompt 字符数。
     */
    private Double avgPromptChars;

    /**
     * 有输入 Token 预算的调用次数。
     */
    private Long budgetSampleCalls;

    /**
     * 输入 Token 超过预算的调用次数。
     */
    private Long budgetExceededCalls;

    /**
     * 输入超预算但未发生上下文裁剪的调用次数。
     */
    private Long budgetUncoveredCalls;

    /**
     * 当前分组累计输入 Token 超预算数量。
     */
    private Long totalInputTokenOverBudget;

    /**
     * 有音频时长样本的语音转写调用次数。
     */
    private Long audioSampleCalls;

    /**
     * 当前分组累计输入音频时长，单位毫秒。
     */
    private Long totalAudioDurationMs;

    /**
     * 有音频样本的平均输入音频时长，单位毫秒。
     */
    private Double avgAudioDurationMs;
}

