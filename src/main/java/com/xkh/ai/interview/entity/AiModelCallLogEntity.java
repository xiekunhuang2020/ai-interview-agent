package com.xkh.ai.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_model_call_log")
public class AiModelCallLogEntity {

    /**
     * 审计记录主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 请求链路追踪编号，用于把一次前端请求和后端日志串起来。
     */
    private String traceId;

    /**
     * 模型调用场景，例如简历诊断、岗位匹配、AI 顾问流式对话。
     */
    private String operationName;

    /**
     * 当前场景使用的 Prompt 版本，方便对比不同 Prompt 的效果。
     */
    private String promptVersion;

    /**
     * 实际调用或配置兜底的模型名称，例如 qwen-max。
     */
    private String modelName;

    /**
     * 模型调用是否成功，1 表示成功，0 表示失败。
     */
    private Integer success;

    /**
     * 本次模型调用总耗时，单位毫秒。
     */
    private Long latencyMs;

    /**
     * 官方返回的输入 Token 数，对应 Prompt 和上下文消耗。
     */
    private Integer inputTokens;

    /**
     * 官方返回的输出 Token 数，对应模型生成内容消耗。
     */
    private Integer outputTokens;

    /**
     * 官方返回的总 Token 数，缺失时由输入和输出相加兜底。
     */
    private Integer totalTokens;

    /**
     * 语音转写上传的音频文件大小，单位字节。
     */
    private Long audioFileSizeBytes;

    /**
     * 语音转写使用的音频采样率。
     */
    private Integer audioSampleRate;

    /**
     * 语音转写输入音频的时长，单位毫秒。
     */
    private Long audioDurationMs;

    /**
     * 当前调用场景配置的目标输入 Token 预算。
     */
    private Integer inputTokenBudget;

    /**
     * 输入 Token 超出目标预算的数量，未超出时为 0。
     */
    private Integer inputTokenOverBudget;

    /**
     * 输入 Token 是否超过目标预算，1 表示超出，0 表示未超出。
     */
    private Integer budgetExceeded;

    /**
     * 输入超预算但上下文未被裁剪，1 表示当前预算策略没有覆盖到主要消耗。
     */
    private Integer budgetUncovered;

    /**
     * 最终发送给模型的 Prompt 字符数，用于观察上下文长度。
     */
    private Integer promptChars;

    /**
     * 上下文是否被预算策略裁剪，1 表示发生裁剪，0 表示未裁剪。
     */
    private Integer contextClipped;

    /**
     * 本次调用累计裁掉的字符数，用于衡量预算策略减少了多少上下文。
     */
    private Integer clippedChars;

    /**
     * 失败时记录的错误摘要，成功时为空。
     */
    private String errorMessage;

    /**
     * 标准化错误类型，例如超时、限流、结构化输出错误。
     */
    private String errorType;

    /**
     * 按后端配置的模型单价估算出的本次调用费用，不落库。
     */
    @TableField(exist = false)
    private BigDecimal estimatedCostCny;

    /**
     * 审计记录创建时间。
     */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;
}

