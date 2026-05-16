package com.xkh.ai.interview.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDescriptionMatchResult {
    private Integer overallScore;
    private String matchLevel;
    private String summary;
    private List<SkillMatch> matchedSkills;
    private List<SkillGap> missingSkills;
    private List<String> interviewFocus;
    private List<String> risks;
    private List<String> learningSuggestions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillMatch {
        private String skill;
        private String evidence;
        private Integer score;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillGap {
        private String skill;
        private String importance;
        private String suggestion;
    }
}
