package com.xkh.ai.interview.service.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xkh.ai.interview.entity.AiModelCallLogEntity;
import com.xkh.ai.interview.mapper.AiModelCallLogMapper;
import com.xkh.ai.interview.dto.PromptFailureReasonResultDTO;
import com.xkh.ai.interview.dto.PromptMetricsResultDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Service
public class AiModelCallAuditQueryService {

    private static final String ERROR_TYPE_STRUCTURED_OUTPUT_ERROR = "STRUCTURED_OUTPUT_ERROR";

    private final AiModelCallLogMapper aiModelCallLogMapper;

    /**
     * 注入模型调用审计 Mapper，用于查询调用日志和聚合指标。
     */
    public AiModelCallAuditQueryService(AiModelCallLogMapper aiModelCallLogMapper) {
        this.aiModelCallLogMapper = aiModelCallLogMapper;
    }

    /**
     * 查询最近的模型调用记录，可按 traceId 或 operationName 缩小排查范围。
     */
    public List<AiModelCallLogEntity> listRecent(String traceId, String operationName, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<AiModelCallLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(traceId), AiModelCallLogEntity::getTraceId, traceId);
        wrapper.eq(StringUtils.isNotBlank(operationName), AiModelCallLogEntity::getOperationName, operationName);
        wrapper.orderByDesc(AiModelCallLogEntity::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);
        return aiModelCallLogMapper.selectList(wrapper);
    }

    /**
     * 按 operationName 和 Prompt 版本聚合调用次数、成功率、失败数、耗时和 Token 用量指标。
     */
    public List<PromptMetricsResultDTO> listPromptMetrics(String operationName, String promptVersion, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        LambdaQueryWrapper<AiModelCallLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(operationName), AiModelCallLogEntity::getOperationName, operationName);
        wrapper.eq(StringUtils.isNotBlank(promptVersion), AiModelCallLogEntity::getPromptVersion, promptVersion);
        wrapper.orderByDesc(AiModelCallLogEntity::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);

        List<AiModelCallLogEntity> logs = aiModelCallLogMapper.selectList(wrapper);
        Map<String, List<AiModelCallLogEntity>> groupedLogs = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getOperationName() + "::" + log.getPromptVersion()));

        return groupedLogs.values().stream()
                .map(this::toMetrics)
                .sorted(Comparator.comparing(PromptMetricsResultDTO::getTotalCalls).reversed())
                .toList();
    }

    /**
     * 聚合模型调用失败原因，用于 Prompt 看板排查高频失败类型。
     */
    public List<PromptFailureReasonResultDTO> listFailureReasons(String operationName, String promptVersion, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        LambdaQueryWrapper<AiModelCallLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(operationName), AiModelCallLogEntity::getOperationName, operationName);
        wrapper.eq(StringUtils.isNotBlank(promptVersion), AiModelCallLogEntity::getPromptVersion, promptVersion);
        wrapper.eq(AiModelCallLogEntity::getSuccess, 0);
        wrapper.orderByDesc(AiModelCallLogEntity::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);

        List<AiModelCallLogEntity> logs = aiModelCallLogMapper.selectList(wrapper);
        long totalFailures = logs.size();
        Map<String, List<AiModelCallLogEntity>> groupedLogs = logs.stream()
                .collect(Collectors.groupingBy(this::failureTypeKey));

        return groupedLogs.entrySet().stream()
                .map(entry -> toFailureReason(entry.getKey(), entry.getValue(), totalFailures))
                .sorted(Comparator.comparing(PromptFailureReasonResultDTO::getCount).reversed())
                .toList();
    }

    /**
     * 查询最近的结构化输出失败记录，用于看板沉淀可复盘的失败样例。
     */
    public List<AiModelCallLogEntity> listStructuredOutputFailures(String operationName,
                                                                   String promptVersion,
                                                                   int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        LambdaQueryWrapper<AiModelCallLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(operationName), AiModelCallLogEntity::getOperationName, operationName);
        wrapper.eq(StringUtils.isNotBlank(promptVersion), AiModelCallLogEntity::getPromptVersion, promptVersion);
        wrapper.eq(AiModelCallLogEntity::getSuccess, 0);
        wrapper.eq(AiModelCallLogEntity::getErrorType, ERROR_TYPE_STRUCTURED_OUTPUT_ERROR);
        wrapper.orderByDesc(AiModelCallLogEntity::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);
        return aiModelCallLogMapper.selectList(wrapper);
    }

    /**
     * 将同一 Prompt 版本下的调用日志转换为前端看板需要的指标 DTO。
     */
    private PromptMetricsResultDTO toMetrics(List<AiModelCallLogEntity> logs) {
        long totalCalls = logs.size();
        long successCalls = logs.stream().filter(log -> Integer.valueOf(1).equals(log.getSuccess())).count();
        long failedCalls = totalCalls - successCalls;
        double avgLatencyMs = logs.stream()
                .mapToLong(log -> log.getLatencyMs() == null ? 0L : log.getLatencyMs())
                .average()
                .orElse(0D);
        long maxLatencyMs = logs.stream()
                .mapToLong(log -> log.getLatencyMs() == null ? 0L : log.getLatencyMs())
                .max()
                .orElse(0L);
        double avgAttemptCount = logs.stream()
                .mapToInt(log -> log.getAttemptCount() == null ? 0 : log.getAttemptCount())
                .average()
                .orElse(0D);
        String modelNames = modelNamesOf(logs);
        long tokenSampleCalls = logs.stream().filter(this::hasTokenUsage).count();
        long totalInputTokens = logs.stream()
                .mapToLong(log -> log.getInputTokens() == null ? 0L : log.getInputTokens())
                .sum();
        long totalOutputTokens = logs.stream()
                .mapToLong(log -> log.getOutputTokens() == null ? 0L : log.getOutputTokens())
                .sum();
        long totalTokens = logs.stream()
                .mapToLong(this::tokenTotalOf)
                .sum();
        double avgTotalTokens = tokenSampleCalls == 0 ? 0D : totalTokens * 1D / tokenSampleCalls;
        long contextSampleCalls = logs.stream().filter(log -> log.getPromptChars() != null).count();
        long clippedCalls = logs.stream()
                .filter(log -> Integer.valueOf(1).equals(log.getContextClipped()))
                .count();
        long totalPromptChars = logs.stream()
                .mapToLong(log -> log.getPromptChars() == null ? 0L : log.getPromptChars())
                .sum();
        long totalClippedChars = logs.stream()
                .mapToLong(log -> log.getClippedChars() == null ? 0L : log.getClippedChars())
                .sum();
        double avgPromptChars = contextSampleCalls == 0 ? 0D : totalPromptChars * 1D / contextSampleCalls;
        long budgetSampleCalls = logs.stream().filter(log -> log.getInputTokenBudget() != null).count();
        long budgetExceededCalls = logs.stream()
                .filter(log -> Integer.valueOf(1).equals(log.getBudgetExceeded()))
                .count();
        long budgetUncoveredCalls = logs.stream()
                .filter(log -> Integer.valueOf(1).equals(log.getBudgetUncovered()))
                .count();
        long totalInputTokenOverBudget = logs.stream()
                .mapToLong(log -> log.getInputTokenOverBudget() == null ? 0L : log.getInputTokenOverBudget())
                .sum();

        AiModelCallLogEntity sample = logs.get(0);
        return PromptMetricsResultDTO.builder()
                .operationName(sample.getOperationName())
                .promptVersion(sample.getPromptVersion())
                .modelNames(modelNames)
                .totalCalls(totalCalls)
                .successCalls(successCalls)
                .failedCalls(failedCalls)
                .successRate(rate(successCalls, totalCalls))
                .avgLatencyMs(round(avgLatencyMs))
                .maxLatencyMs(maxLatencyMs)
                .avgAttemptCount(round(avgAttemptCount))
                .tokenSampleCalls(tokenSampleCalls)
                .totalInputTokens(totalInputTokens)
                .totalOutputTokens(totalOutputTokens)
                .totalTokens(totalTokens)
                .avgTotalTokens(round(avgTotalTokens))
                .contextSampleCalls(contextSampleCalls)
                .clippedCalls(clippedCalls)
                .totalPromptChars(totalPromptChars)
                .totalClippedChars(totalClippedChars)
                .avgPromptChars(round(avgPromptChars))
                .budgetSampleCalls(budgetSampleCalls)
                .budgetExceededCalls(budgetExceededCalls)
                .budgetUncoveredCalls(budgetUncoveredCalls)
                .totalInputTokenOverBudget(totalInputTokenOverBudget)
                .build();
    }

    /**
     * 聚合同一组调用出现过的模型名称，方便看板识别是否发生模型切换。
     */
    private String modelNamesOf(List<AiModelCallLogEntity> logs) {
        LinkedHashSet<String> modelNames = logs.stream()
                .map(AiModelCallLogEntity::getModelName)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (modelNames.isEmpty()) {
            return null;
        }
        return String.join(" / ", modelNames);
    }

    /**
     * 判断日志是否已经记录模型返回的 Token 用量。
     */
    private boolean hasTokenUsage(AiModelCallLogEntity log) {
        return log.getInputTokens() != null || log.getOutputTokens() != null || log.getTotalTokens() != null;
    }

    /**
     * 获取单次调用总 Token，优先使用官方返回的 totalTokens，缺失时用输入和输出相加兜底。
     */
    private long tokenTotalOf(AiModelCallLogEntity log) {
        if (log.getTotalTokens() != null) {
            return log.getTotalTokens();
        }
        return (log.getInputTokens() == null ? 0L : log.getInputTokens())
                + (log.getOutputTokens() == null ? 0L : log.getOutputTokens());
    }

    /**
     * 将同一失败原因下的日志转换为失败原因分布 DTO。
     */
    private PromptFailureReasonResultDTO toFailureReason(String errorType, List<AiModelCallLogEntity> logs, long totalFailures) {
        AiModelCallLogEntity sample = logs.get(0);
        return PromptFailureReasonResultDTO.builder()
                .operationName(sample.getOperationName())
                .promptVersion(sample.getPromptVersion())
                .errorType(errorType)
                .reason(failureReasonKey(sample))
                .count((long) logs.size())
                .percentage(rate(logs.size(), totalFailures))
                .build();
    }

    /**
     * 生成失败类型聚合键，优先使用审计表中的标准化 errorType。
     */
    private String failureTypeKey(AiModelCallLogEntity log) {
        if (StringUtils.isBlank(log.getErrorType())) {
            return "UNKNOWN";
        }
        return log.getErrorType();
    }

    /**
     * 生成失败原因聚合键，避免完整异常堆栈导致同类错误无法归并。
     */
    private String failureReasonKey(AiModelCallLogEntity log) {
        if (StringUtils.isBlank(log.getErrorMessage())) {
            return "unknown";
        }
        String reason = log.getErrorMessage().replaceAll("\\s+", " ").trim();
        if (reason.length() > 120) {
            return reason.substring(0, 120);
        }
        return reason;
    }

    /**
     * 计算百分比并统一保留两位小数。
     */
    private double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0D;
        }
        return round(numerator * 100D / denominator);
    }

    /**
     * 将浮点数四舍五入到两位小数，保证看板展示稳定。
     */
    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }
}

