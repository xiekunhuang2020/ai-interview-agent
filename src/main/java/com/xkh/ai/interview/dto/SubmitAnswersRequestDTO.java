package com.xkh.ai.interview.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SubmitAnswersRequestDTO {

    /**
     * 候选人提交的答案，key 为题目序号，从 0 开始。
     */
    private Map<Integer, String> answers;

    /**
     * 使用语音转写作答的题目序号，用于让复盘报告补充语音表达建议。
     */
    @Size(max = 200)
    private List<Integer> voiceAnswerIndexes;
}
