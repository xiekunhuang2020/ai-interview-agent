package com.xkh.ai.interview.dto;

import lombok.Data;

@Data
public class AgentChatRequest {
    private String message;
    private String conversationId;
}
