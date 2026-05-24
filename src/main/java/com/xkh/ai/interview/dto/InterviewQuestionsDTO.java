package com.xkh.ai.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 面试问题
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestionsDTO {
    
    /**
     * 问题列表
     */
    @Valid
    @NotEmpty
    @Size(max = 20)
    private List<@Valid Question> questions;

    @Data
    @NoArgsConstructor
    public static class Question {
        private static final String CURRENT_RESUME_FACT = "CURRENT_RESUME_FACT";
        private static final String SIMILAR_RESUME_REFERENCE = "SIMILAR_RESUME_REFERENCE";
        private static final int MAX_SOURCE_NOTE_CHARS = 120;
        private static final Set<String> ALLOWED_QUESTION_TYPES = Set.of(
                "PROJECT",
                "JAVA_BASIC",
                "JAVA_COLLECTION",
                "JAVA_CONCURRENT",
                "MYSQL",
                "REDIS",
                "SPRING",
                "SPRING_BOOT",
                "AI"
        );
        private static final Set<String> ALLOWED_EVIDENCE_SOURCES = Set.of(
                CURRENT_RESUME_FACT,
                SIMILAR_RESUME_REFERENCE
        );
        private static final Map<String, String> QUESTION_TYPE_ALIASES = Map.ofEntries(
                Map.entry("JAVA", "JAVA_BASIC"),
                Map.entry("JAVA_CORE", "JAVA_BASIC"),
                Map.entry("JVM", "JAVA_BASIC"),
                Map.entry("COLLECTION", "JAVA_COLLECTION"),
                Map.entry("COLLECTIONS", "JAVA_COLLECTION"),
                Map.entry("CONCURRENT", "JAVA_CONCURRENT"),
                Map.entry("THREAD", "JAVA_CONCURRENT"),
                Map.entry("THREADING", "JAVA_CONCURRENT"),
                Map.entry("DATABASE", "MYSQL"),
                Map.entry("DB", "MYSQL"),
                Map.entry("SQL", "MYSQL"),
                Map.entry("CACHE", "REDIS"),
                Map.entry("SPRINGBOOT", "SPRING_BOOT"),
                Map.entry("SPRING AI", "AI"),
                Map.entry("SPRING_AI", "AI"),
                Map.entry("RAG", "AI"),
                Map.entry("AI_AGENT", "AI"),
                Map.entry("AGENT", "AI"),
                Map.entry("SYSTEM_DESIGN", "PROJECT"),
                Map.entry("ARCHITECTURE", "PROJECT"),
                Map.entry("DESIGN", "PROJECT"),
                Map.entry("项目", "PROJECT"),
                Map.entry("项目经历", "PROJECT"),
                Map.entry("系统设计", "PROJECT")
        );
        private static final Map<String, String> EVIDENCE_SOURCE_ALIASES = Map.ofEntries(
                Map.entry("CURRENT", CURRENT_RESUME_FACT),
                Map.entry("RESUME", CURRENT_RESUME_FACT),
                Map.entry("CURRENT_RESUME", CURRENT_RESUME_FACT),
                Map.entry("FACT", CURRENT_RESUME_FACT),
                Map.entry("SIMILAR", SIMILAR_RESUME_REFERENCE),
                Map.entry("REFERENCE", SIMILAR_RESUME_REFERENCE),
                Map.entry("RAG", SIMILAR_RESUME_REFERENCE),
                Map.entry("SIMILAR_RESUME", SIMILAR_RESUME_REFERENCE),
                Map.entry("当前简历", CURRENT_RESUME_FACT),
                Map.entry("当前简历事实", CURRENT_RESUME_FACT),
                Map.entry("相似简历", SIMILAR_RESUME_REFERENCE),
                Map.entry("相似简历参考", SIMILAR_RESUME_REFERENCE)
        );

        /**
         * 问题内容
         */
        @NotBlank
        @Size(max = 1000)
        private String question;
        
        /**
         * 问题类型
         */
        @NotBlank
        @Pattern(regexp = "PROJECT|JAVA_BASIC|JAVA_COLLECTION|JAVA_CONCURRENT|MYSQL|REDIS|SPRING|SPRING_BOOT|AI")
        private String type;
        
        /**
         * 细分类别
         */
        @NotBlank
        @Size(max = 128)
        private String category;

        /**
         * 问题依据来源：当前简历事实或相似简历参考。
         */
        @NotBlank
        @Pattern(regexp = "CURRENT_RESUME_FACT|SIMILAR_RESUME_REFERENCE")
        private String evidenceSource = CURRENT_RESUME_FACT;

        /**
         * 给前端和面试复盘展示的简短来源说明。
         */
        @Size(max = MAX_SOURCE_NOTE_CHARS)
        private String sourceNote = defaultSourceNote(CURRENT_RESUME_FACT);

        /**
         * 兼容基础出题和历史数据的三字段构造方式，来源字段由 DTO 默认值补齐。
         */
        public Question(String question, String type, String category) {
            this(question, type, category, null, null);
        }

        /**
         * 创建面试题并归一化模型可能输出的别名枚举。
         */
        public Question(String question, String type, String category, String evidenceSource, String sourceNote) {
            setQuestion(question);
            setType(type);
            setCategory(category);
            setEvidenceSource(evidenceSource);
            setSourceNote(sourceNote);
        }

        /**
         * 设置问题内容，并清理首尾空白。
         */
        public void setQuestion(String question) {
            this.question = trimToNull(question);
        }

        /**
         * 设置问题类型，并把模型偶发输出的同义类型归一到允许枚举。
         */
        public void setType(String type) {
            this.type = normalizeQuestionType(type);
        }

        /**
         * 设置问题细分类别，并清理首尾空白。
         */
        public void setCategory(String category) {
            this.category = trimToNull(category);
        }

        /**
         * 设置问题依据来源，并同步刷新默认来源说明。
         */
        public void setEvidenceSource(String evidenceSource) {
            boolean shouldRefreshDefaultNote = isBlank(sourceNote) || isDefaultSourceNote(sourceNote);
            this.evidenceSource = normalizeEvidenceSource(evidenceSource);
            if (shouldRefreshDefaultNote) {
                this.sourceNote = defaultSourceNote(this.evidenceSource);
            }
        }

        /**
         * 设置问题来源说明，空值使用当前来源类型对应的默认说明。
         */
        public void setSourceNote(String sourceNote) {
            this.sourceNote = normalizeSourceNote(sourceNote, evidenceSource);
        }

        /**
         * 将问题类型归一化到 DTO 允许的枚举。
         */
        private String normalizeQuestionType(String type) {
            if (isBlank(type)) {
                return "PROJECT";
            }

            String normalized = type.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (ALLOWED_QUESTION_TYPES.contains(normalized)) {
                return normalized;
            }
            return QUESTION_TYPE_ALIASES.getOrDefault(normalized, "PROJECT");
        }

        /**
         * 将问题来源归一化到 DTO 允许的枚举。
         */
        private String normalizeEvidenceSource(String evidenceSource) {
            if (isBlank(evidenceSource)) {
                return CURRENT_RESUME_FACT;
            }

            String normalized = evidenceSource.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (ALLOWED_EVIDENCE_SOURCES.contains(normalized)) {
                return normalized;
            }
            return EVIDENCE_SOURCE_ALIASES.getOrDefault(
                    normalized,
                    EVIDENCE_SOURCE_ALIASES.getOrDefault(evidenceSource.trim(), CURRENT_RESUME_FACT)
            );
        }

        /**
         * 生成当前来源类型对应的默认来源说明。
         */
        private static String defaultSourceNote(String evidenceSource) {
            if (SIMILAR_RESUME_REFERENCE.equals(evidenceSource)) {
                return "参考相似简历片段设计追问";
            }
            return "基于当前简历事实设计追问";
        }

        /**
         * 规整来源说明长度，避免页面展示和数据库字段被撑开。
         */
        private String normalizeSourceNote(String sourceNote, String evidenceSource) {
            String normalizedNote = isBlank(sourceNote) ? defaultSourceNote(evidenceSource) : sourceNote.trim();
            if (normalizedNote.length() <= MAX_SOURCE_NOTE_CHARS) {
                return normalizedNote;
            }
            return normalizedNote.substring(0, MAX_SOURCE_NOTE_CHARS);
        }

        /**
         * 判断来源说明是否仍是系统默认值。
         */
        private boolean isDefaultSourceNote(String sourceNote) {
            return defaultSourceNote(CURRENT_RESUME_FACT).equals(sourceNote)
                    || defaultSourceNote(SIMILAR_RESUME_REFERENCE).equals(sourceNote);
        }

        /**
         * 将空白字符串统一转成 null，让 Bean Validation 输出明确错误。
         */
        private String trimToNull(String value) {
            if (isBlank(value)) {
                return null;
            }
            return value.trim();
        }

        /**
         * 判断字符串是否为空白。
         */
        private boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}

