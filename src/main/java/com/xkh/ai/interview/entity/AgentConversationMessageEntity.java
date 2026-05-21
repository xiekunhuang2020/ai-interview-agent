package com.xkh.ai.interview.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_conversation_message")
public class AgentConversationMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private String turnId;

    private String traceId;

    private String agentName;

    private String role;

    private String messageContent;

    private Integer success;

    private Long latencyMs;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

