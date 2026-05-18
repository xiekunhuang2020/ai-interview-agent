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
public class InterviewQuestions {
    
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
    }
}
