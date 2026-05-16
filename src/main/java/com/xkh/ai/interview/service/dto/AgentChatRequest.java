package com.xkh.ai.interview.service.dto;

import lombok.Data;

@Data
public class AgentChatRequest {
    private String message;
    private String conversationId;
}
