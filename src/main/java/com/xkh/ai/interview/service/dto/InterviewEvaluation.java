package com.xkh.ai.interview.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 面试评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewEvaluation {
    
    /**
     * 面试会话 ID
     */
    private String sessionId;
    
    /**
     * 总问题数
     */
    private Integer totalQuestions;
    
    /**
     * 总分
     */
    private Integer overallScore;
    
    /**
     * 分类得分统计
     */
    private List<CategoryScore> categoryScores;
    
    /**
     * 问题详情
     */
    private List<QuestionDetail> questionDetails;
    
    /**
     * 整体反馈
     */
    private String overallFeedback;
    
    /**
     * 优势列表
     */
    private List<String> strengths;
    
    /**
     * 改进建议列表
     */
    private List<String> improvements;
    
    /**
     * 参考答案列表
     */
    private List<ReferenceAnswer> referenceAnswers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryScore {
        private String category;
        private Integer score;
        private Integer questionCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDetail {
        private Integer questionIndex;
        private String question;
        private String category;
        private String userAnswer;
        private Integer score;
        private String feedback;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceAnswer {
        private Integer questionIndex;
        private String question;
        private String referenceAnswer;
        private List<String> keyPoints;
    }
}
