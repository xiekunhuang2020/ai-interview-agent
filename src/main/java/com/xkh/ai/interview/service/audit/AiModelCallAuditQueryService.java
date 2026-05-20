package com.xkh.ai.interview.service.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xkh.ai.interview.entity.AiModelCallLog;
import com.xkh.ai.interview.mapper.AiModelCallLogMapper;
import com.xkh.ai.interview.dto.PromptFailureReasonResult;
import com.xkh.ai.interview.dto.PromptMetricsResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiModelCallAuditQueryService {

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
    public List<AiModelCallLog> listRecent(String traceId, String operationName, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<AiModelCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(traceId), AiModelCallLog::getTraceId, traceId);
        wrapper.eq(StringUtils.isNotBlank(operationName), AiModelCallLog::getOperationName, operationName);
        wrapper.orderByDesc(AiModelCallLog::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);
        return aiModelCallLogMapper.selectList(wrapper);
    }

    /**
     * 按 operationName 和 Prompt 版本聚合调用次数、成功率、失败数和耗时指标。
     */
    public List<PromptMetricsResult> listPromptMetrics(String operationName, String promptVersion, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        LambdaQueryWrapper<AiModelCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(operationName), AiModelCallLog::getOperationName, operationName);
        wrapper.eq(StringUtils.isNotBlank(promptVersion), AiModelCallLog::getPromptVersion, promptVersion);
        wrapper.orderByDesc(AiModelCallLog::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);

        List<AiModelCallLog> logs = aiModelCallLogMapper.selectList(wrapper);
        Map<String, List<AiModelCallLog>> groupedLogs = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getOperationName() + "::" + log.getPromptVersion()));

        return groupedLogs.values().stream()
                .map(this::toMetrics)
                .sorted(Comparator.comparing(PromptMetricsResult::getTotalCalls).reversed())
                .toList();
    }

    /**
     * 聚合模型调用失败原因，用于 Prompt 看板排查高频失败类型。
     */
    public List<PromptFailureReasonResult> listFailureReasons(String operationName, String promptVersion, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        LambdaQueryWrapper<AiModelCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(operationName), AiModelCallLog::getOperationName, operationName);
        wrapper.eq(StringUtils.isNotBlank(promptVersion), AiModelCallLog::getPromptVersion, promptVersion);
        wrapper.eq(AiModelCallLog::getSuccess, 0);
        wrapper.orderByDesc(AiModelCallLog::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);

        List<AiModelCallLog> logs = aiModelCallLogMapper.selectList(wrapper);
        long totalFailures = logs.size();
        Map<String, List<AiModelCallLog>> groupedLogs = logs.stream()
                .collect(Collectors.groupingBy(this::failureReasonKey));

        return groupedLogs.entrySet().stream()
                .map(entry -> toFailureReason(entry.getKey(), entry.getValue(), totalFailures))
                .sorted(Comparator.comparing(PromptFailureReasonResult::getCount).reversed())
                .toList();
    }

    /**
     * 将同一 Prompt 版本下的调用日志转换为前端看板需要的指标 DTO。
     */
    private PromptMetricsResult toMetrics(List<AiModelCallLog> logs) {
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

        AiModelCallLog sample = logs.get(0);
        return PromptMetricsResult.builder()
                .operationName(sample.getOperationName())
                .promptVersion(sample.getPromptVersion())
                .totalCalls(totalCalls)
                .successCalls(successCalls)
                .failedCalls(failedCalls)
                .successRate(rate(successCalls, totalCalls))
                .avgLatencyMs(round(avgLatencyMs))
                .maxLatencyMs(maxLatencyMs)
                .avgAttemptCount(round(avgAttemptCount))
                .build();
    }

    /**
     * 将同一失败原因下的日志转换为失败原因分布 DTO。
     */
    private PromptFailureReasonResult toFailureReason(String reason, List<AiModelCallLog> logs, long totalFailures) {
        AiModelCallLog sample = logs.get(0);
        return PromptFailureReasonResult.builder()
                .operationName(sample.getOperationName())
                .promptVersion(sample.getPromptVersion())
                .reason(reason)
                .count((long) logs.size())
                .percentage(rate(logs.size(), totalFailures))
                .build();
    }

    /**
     * 生成失败原因聚合键，避免完整异常堆栈导致同类错误无法归并。
     */
    private String failureReasonKey(AiModelCallLog log) {
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
