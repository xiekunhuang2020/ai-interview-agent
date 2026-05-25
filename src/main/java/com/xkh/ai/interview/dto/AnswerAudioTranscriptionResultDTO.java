package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AnswerAudioTranscriptionResultDTO {

    /**
     * 语音回答转写后的文本内容，前端会回填到对应回答框。
     */
    private String text;

    /**
     * 用户上传到后端的音频文件名，方便排查浏览器录音格式问题。
     */
    private String originalFileName;

    /**
     * 音频文件大小，单位为字节。
     */
    private long fileSize;

    /**
     * 本次转写使用的语音识别模型名称。
     */
    private String modelName;
}
