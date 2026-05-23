package com.xkh.ai.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    @NotNull
    private List<@Valid Question> questions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Question {
        /**
         * 问题内容
         */
        @NotNull
        private String question;
        
        /**
         * 问题类型
         */
        @NotNull
        @Pattern(regexp = "PROJECT|JAVA_BASIC|JAVA_COLLECTION|JAVA_CONCURRENT|MYSQL|REDIS|SPRING|SPRING_BOOT|AI")
        private String type;
        
        /**
         * 细分类别
         */
        @NotNull
        private String category;

        /**
         * 问题依据来源：当前简历事实或相似简历参考。
         */
        @Pattern(regexp = "CURRENT_RESUME_FACT|SIMILAR_RESUME_REFERENCE")
        private String evidenceSource;

        /**
         * 给前端和面试复盘展示的简短来源说明。
         */
        private String sourceNote;

        /**
         * 兼容基础出题和历史数据的三字段构造方式，来源字段由解析器或业务层补齐。
         */
        public Question(String question, String type, String category) {
            this.question = question;
            this.type = type;
            this.category = category;
        }
    }
}

