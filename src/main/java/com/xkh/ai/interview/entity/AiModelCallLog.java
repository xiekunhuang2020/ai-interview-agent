package com.xkh.ai.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model_call_log")
public class AiModelCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private String operationName;

    private String promptVersion;

    private Integer success;

    private Integer attemptCount;

    private Long latencyMs;

    private String errorMessage;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;
}
