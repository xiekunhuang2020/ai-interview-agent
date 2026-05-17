package com.xkh.ai.interview.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @NotNull
    @Min(0)
    @Max(100)
    private Integer overallScore;
    @NotNull
    @Pattern(regexp = "高度匹配|较匹配|一般匹配|匹配度较低")
    private String matchLevel;
    @NotNull
    private String summary;
    @Valid
    @NotNull
    private List<@Valid SkillMatch> matchedSkills;
    @Valid
    @NotNull
    private List<@Valid SkillGap> missingSkills;
    @NotNull
    private List<@NotNull String> interviewFocus;
    @NotNull
    private List<@NotNull String> risks;
    @NotNull
    private List<@NotNull String> learningSuggestions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillMatch {
        @NotNull
        private String skill;
        @NotNull
        private String evidence;
        @NotNull
        @Min(0)
        @Max(100)
        private Integer score;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillGap {
        @NotNull
        private String skill;
        @NotNull
        @Pattern(regexp = "高|中|低")
        private String importance;
        @NotNull
        private String suggestion;
    }
}
