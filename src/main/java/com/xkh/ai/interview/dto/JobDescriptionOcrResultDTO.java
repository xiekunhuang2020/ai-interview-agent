package com.xkh.ai.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JobDescriptionOcrResultDTO {

    /**
     * 从岗位截图中识别出的岗位说明文本。
     */
    private String jobDescription;

    /**
     * 用户上传的原始截图文件名，方便前端提示识别来源。
     */
    private String originalFileName;

    /**
     * 截图文件大小，单位为字节。
     */
    private long fileSize;
}
