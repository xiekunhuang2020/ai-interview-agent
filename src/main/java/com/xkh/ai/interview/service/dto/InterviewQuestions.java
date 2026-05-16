package com.xkh.ai.interview.service.dto;

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
    private List<Question> questions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Question {
        /**
         * 问题内容
         */
        private String question;
        
        /**
         * 问题类型
         */
        private String type;
        
        /**
         * 细分类别
         */
        private String category;
    }
}
