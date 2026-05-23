package com.xkh.ai.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDescriptionMatchResultDTO {
    @NotNull
    @Min(0)
    @Max(100)
    private Integer overallScore;
    @NotBlank
    @Pattern(regexp = "高度匹配|较匹配|一般匹配|匹配度较低")
    private String matchLevel;
    @NotBlank
    @Size(max = 2000)
    private String summary;
    @Valid
    @NotNull
    @Size(max = 20)
    private List<@Valid SkillMatch> matchedSkills;
    @Valid
    @NotNull
    @Size(max = 20)
    private List<@Valid SkillGap> missingSkills;
    @NotNull
    @Size(max = 20)
    private List<@NotBlank String> interviewFocus;
    @NotNull
    @Size(max = 20)
    private List<@NotBlank String> risks;
    @NotNull
    @Size(max = 20)
    private List<@NotBlank String> learningSuggestions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillMatch {
        @NotBlank
        @Size(max = 128)
        private String skill;
        @NotBlank
        @Size(max = 1000)
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
        @NotBlank
        @Size(max = 128)
        private String skill;
        @NotBlank
        @Pattern(regexp = "高|中|低")
        private String importance;
        @NotBlank
        @Size(max = 1000)
        private String suggestion;
    }
}

