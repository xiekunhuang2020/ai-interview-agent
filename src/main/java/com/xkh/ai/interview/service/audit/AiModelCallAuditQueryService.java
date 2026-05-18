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

    public AiModelCallAuditQueryService(AiModelCallLogMapper aiModelCallLogMapper) {
        this.aiModelCallLogMapper = aiModelCallLogMapper;
    }

    public List<AiModelCallLog> listRecent(String traceId, String operationName, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<AiModelCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(traceId), AiModelCallLog::getTraceId, traceId);
        wrapper.eq(StringUtils.isNotBlank(operationName), AiModelCallLog::getOperationName, operationName);
        wrapper.orderByDesc(AiModelCallLog::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);
        return aiModelCallLogMapper.selectList(wrapper);
    }

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

    private PromptMetricsResult toMetrics(List<AiModelCallLog> logs) {
        long totalCalls = logs.size();
        long successCalls = logs.stream().filter(log -> Integer.valueOf(1).equals(log.getSuccess())).count();
        long fallbackCalls = logs.stream().filter(log -> Integer.valueOf(1).equals(log.getFallbackUsed())).count();
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
                .fallbackCalls(fallbackCalls)
                .successRate(rate(successCalls, totalCalls))
                .fallbackRate(rate(fallbackCalls, totalCalls))
                .avgLatencyMs(round(avgLatencyMs))
                .maxLatencyMs(maxLatencyMs)
                .avgAttemptCount(round(avgAttemptCount))
                .build();
    }

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

    private double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0D;
        }
        return round(numerator * 100D / denominator);
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
