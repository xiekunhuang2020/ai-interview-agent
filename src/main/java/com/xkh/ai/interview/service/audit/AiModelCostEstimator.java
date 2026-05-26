package com.xkh.ai.interview.service.audit;

import com.xkh.ai.interview.entity.AiModelCallLogEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "ai-interview.cost")
public class AiModelCostEstimator {

    private Map<String, ChatModelPrice> chatModels = new HashMap<>();
    private Map<String, AudioModelPrice> audioModels = new HashMap<>();

    /**
     * 根据单次模型调用的 token usage 和音频时长估算费用。
     */
    public BigDecimal estimate(AiModelCallLogEntity log) {
        if (log == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal textCost = estimateTextCost(log.getModelName(), log.getInputTokens(), log.getOutputTokens());
        BigDecimal audioCost = estimateAudioCost(log.getModelName(), log.getAudioDurationMs());
        return normalize(textCost.add(audioCost));
    }

    /**
     * 将累计费用保留到 4 位小数，前端只负责格式化展示。
     */
    public BigDecimal normalize(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 估算文本模型输入和输出 token 的费用。
     */
    private BigDecimal estimateTextCost(String modelName, Integer inputTokens, Integer outputTokens) {
        if ((inputTokens == null || inputTokens <= 0) && (outputTokens == null || outputTokens <= 0)) {
            return BigDecimal.ZERO;
        }
        ChatModelPrice price = matchChatModelPrice(modelName);
        if (price == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal inputCost = price.inputPerMillionTokens
                .multiply(BigDecimal.valueOf(inputTokens == null ? 0 : inputTokens))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
        BigDecimal outputCost = price.outputPerMillionTokens
                .multiply(BigDecimal.valueOf(outputTokens == null ? 0 : outputTokens))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
        return inputCost.add(outputCost);
    }

    /**
     * 估算语音识别按秒计费的费用。
     */
    private BigDecimal estimateAudioCost(String modelName, Long audioDurationMs) {
        if (audioDurationMs == null || audioDurationMs <= 0) {
            return BigDecimal.ZERO;
        }
        AudioModelPrice price = matchAudioModelPrice(modelName);
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return price.perSecond.multiply(BigDecimal.valueOf(audioDurationMs))
                .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
    }

    /**
     * 根据模型名匹配文本模型价格，优先精确匹配，再做包含匹配。
     */
    private ChatModelPrice matchChatModelPrice(String modelName) {
        String normalizedModelName = normalizeModelName(modelName);
        if (StringUtils.isBlank(normalizedModelName)) {
            return null;
        }
        ChatModelPrice exact = chatModels.get(normalizedModelName);
        if (exact != null) {
            return exact;
        }
        return chatModels.entrySet().stream()
                .filter(entry -> normalizedModelName.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据模型名匹配语音模型价格；模型缺失时使用唯一配置作为兜底。
     */
    private AudioModelPrice matchAudioModelPrice(String modelName) {
        String normalizedModelName = normalizeModelName(modelName);
        if (StringUtils.isNotBlank(normalizedModelName)) {
            AudioModelPrice exact = audioModels.get(normalizedModelName);
            if (exact != null) {
                return exact;
            }
            AudioModelPrice contains = audioModels.entrySet().stream()
                    .filter(entry -> normalizedModelName.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            if (contains != null) {
                return contains;
            }
        }
        return audioModels.size() == 1 ? audioModels.values().iterator().next() : null;
    }

    /**
     * 统一模型名大小写，方便配置和审计记录匹配。
     */
    private String normalizeModelName(String modelName) {
        return StringUtils.trimToEmpty(modelName).toLowerCase(Locale.ROOT);
    }

    public Map<String, ChatModelPrice> getChatModels() {
        return chatModels;
    }

    public void setChatModels(Map<String, ChatModelPrice> chatModels) {
        this.chatModels = normalizeKeys(chatModels);
    }

    public Map<String, AudioModelPrice> getAudioModels() {
        return audioModels;
    }

    public void setAudioModels(Map<String, AudioModelPrice> audioModels) {
        this.audioModels = normalizeKeys(audioModels);
    }

    private <T> Map<String, T> normalizeKeys(Map<String, T> values) {
        Map<String, T> result = new HashMap<>();
        if (values != null) {
            values.forEach((key, value) -> result.put(normalizeModelName(key), value));
        }
        return result;
    }

    public static class ChatModelPrice {
        private BigDecimal inputPerMillionTokens = BigDecimal.ZERO;
        private BigDecimal outputPerMillionTokens = BigDecimal.ZERO;

        public BigDecimal getInputPerMillionTokens() {
            return inputPerMillionTokens;
        }

        public void setInputPerMillionTokens(BigDecimal inputPerMillionTokens) {
            this.inputPerMillionTokens = inputPerMillionTokens;
        }

        public BigDecimal getOutputPerMillionTokens() {
            return outputPerMillionTokens;
        }

        public void setOutputPerMillionTokens(BigDecimal outputPerMillionTokens) {
            this.outputPerMillionTokens = outputPerMillionTokens;
        }
    }

    public static class AudioModelPrice {
        private BigDecimal perSecond = BigDecimal.ZERO;

        public BigDecimal getPerSecond() {
            return perSecond;
        }

        public void setPerSecond(BigDecimal perSecond) {
            this.perSecond = perSecond;
        }
    }
}
