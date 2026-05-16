package com.xkh.ai.interview.support;

import com.xkh.ai.interview.entity.AgentConversationMessage;
import com.xkh.ai.interview.mapper.AgentConversationMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AgentConversationAuditRecorder {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    private static final Logger logger = LoggerFactory.getLogger(AgentConversationAuditRecorder.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final AgentConversationMessageMapper agentConversationMessageMapper;
    private final boolean enabled;
    private final boolean logMessageContent;
    private final int maxMessageContentLength;

    public AgentConversationAuditRecorder(
            AgentConversationMessageMapper agentConversationMessageMapper,
            @Value("${ai-interview.agent.audit.enabled:true}") boolean enabled,
            @Value("${ai-interview.agent.audit.log-message-content:true}") boolean logMessageContent,
            @Value("${ai-interview.agent.audit.max-message-content-length:4000}") int maxMessageContentLength) {
        this.agentConversationMessageMapper = agentConversationMessageMapper;
        this.enabled = enabled;
        this.logMessageContent = logMessageContent;
        this.maxMessageContentLength = Math.max(0, maxMessageContentLength);
    }

    @Transactional
    public void recordUserMessage(String conversationId,
                                  String turnId,
                                  String agentName,
                                  String messageContent) {
        record(conversationId, turnId, agentName, ROLE_USER, messageContent, true, 0L, null);
    }

    @Transactional
    public void recordAssistantMessage(String conversationId,
                                       String turnId,
                                       String agentName,
                                       String messageContent,
                                       boolean success,
                                       long latencyMs,
                                       String errorMessage) {
        record(conversationId, turnId, agentName, ROLE_ASSISTANT, messageContent, success, latencyMs, errorMessage);
    }

    private void record(String conversationId,
                        String turnId,
                        String agentName,
                        String role,
                        String messageContent,
                        boolean success,
                        long latencyMs,
                        String errorMessage) {
        if (!enabled) {
            return;
        }
        try {
            AgentConversationMessage message = new AgentConversationMessage();
            message.setConversationId(conversationId);
            message.setTurnId(turnId);
            message.setTraceId(MDC.get(RequestTraceFilter.TRACE_ID_KEY));
            message.setAgentName(agentName);
            message.setRole(role);
            message.setMessageContent(normalizeMessageContent(messageContent));
            message.setSuccess(success ? 1 : 0);
            message.setLatencyMs(Math.max(0L, latencyMs));
            message.setErrorMessage(truncate(errorMessage));
            agentConversationMessageMapper.insert(message);
        } catch (Exception e) {
            logger.warn("Failed to record agent conversation audit, conversationId={}, turnId={}, role={}, error={}",
                    conversationId, turnId, role, e.getMessage());
        }
    }

    private String truncate(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private String normalizeMessageContent(String messageContent) {
        if (!logMessageContent || messageContent == null || maxMessageContentLength == 0) {
            return null;
        }
        if (messageContent.length() <= maxMessageContentLength) {
            return messageContent;
        }
        return messageContent.substring(0, maxMessageContentLength);
    }
}
