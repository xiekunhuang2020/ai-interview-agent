package com.xkh.ai.interview.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {
    private String conversationId;
    private String turnId;
    private String agentName;
    private String answer;
    private Long latencyMs;
}
