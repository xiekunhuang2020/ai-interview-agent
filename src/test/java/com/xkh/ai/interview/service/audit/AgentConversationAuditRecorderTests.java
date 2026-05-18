package com.xkh.ai.interview.service.audit;

import com.xkh.ai.interview.config.RequestTraceFilter;
import com.xkh.ai.interview.entity.AgentConversationMessage;
import com.xkh.ai.interview.mapper.AgentConversationMessageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConversationAuditRecorderTests {

    private AtomicReference<AgentConversationMessage> captured;
    private AgentConversationAuditRecorder recorder;

    @BeforeEach
    void setUp() {
        captured = new AtomicReference<>();
        recorder = new AgentConversationAuditRecorder(capturingMapper(captured), true, true, 4000);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void recordsUserMessageWithTraceId() {
        MDC.put(RequestTraceFilter.TRACE_ID_KEY, "trace-001");

        recorder.recordUserMessage("conversation-001", "turn-001", "interview_agent", "帮我分析这份简历");

        AgentConversationMessage message = capturedMessage();
        assertEquals("conversation-001", message.getConversationId());
        assertEquals("turn-001", message.getTurnId());
        assertEquals("trace-001", message.getTraceId());
        assertEquals("interview_agent", message.getAgentName());
        assertEquals(AgentConversationAuditRecorder.ROLE_USER, message.getRole());
        assertEquals("帮我分析这份简历", message.getMessageContent());
        assertEquals(1, message.getSuccess());
        assertEquals(0L, message.getLatencyMs());
        assertNull(message.getErrorMessage());
    }

    @Test
    void recordsFailedAssistantMessageAndTruncatesError() {
        String longError = "x".repeat(1100);

        recorder.recordAssistantMessage(
                "conversation-001", "turn-001", "interview_agent", null, false, 320L, longError);

        AgentConversationMessage message = capturedMessage();
        assertEquals(AgentConversationAuditRecorder.ROLE_ASSISTANT, message.getRole());
        assertEquals(0, message.getSuccess());
        assertEquals(320L, message.getLatencyMs());
        assertNull(message.getMessageContent());
        assertEquals(1024, message.getErrorMessage().length());
        assertTrue(message.getErrorMessage().contains("...[truncated, originalLength=1100]"));
    }

    @Test
    void canHideMessageContentForSensitiveConversations() {
        AgentConversationAuditRecorder contentHiddenRecorder =
                new AgentConversationAuditRecorder(capturingMapper(captured), true, false, 4000);

        contentHiddenRecorder.recordUserMessage("conversation-001", "turn-001", "interview_agent", "敏感简历内容");

        assertNull(capturedMessage().getMessageContent());
    }

    @Test
    void truncatesLongMessageContentWithMarker() {
        AgentConversationAuditRecorder shortContentRecorder =
                new AgentConversationAuditRecorder(capturingMapper(captured), true, true, 80);

        shortContentRecorder.recordAssistantMessage(
                "conversation-001", "turn-001", "interview_agent", "a".repeat(120), true, 120L, null);

        AgentConversationMessage message = capturedMessage();
        assertEquals(80, message.getMessageContent().length());
        assertTrue(message.getMessageContent().contains("...[truncated, originalLength=120]"));
    }

    private AgentConversationMessage capturedMessage() {
        AgentConversationMessage message = captured.get();
        assertNotNull(message);
        return message;
    }

    private AgentConversationMessageMapper capturingMapper(AtomicReference<AgentConversationMessage> captured) {
        return (AgentConversationMessageMapper) Proxy.newProxyInstance(
                AgentConversationMessageMapper.class.getClassLoader(),
                new Class<?>[]{AgentConversationMessageMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        captured.set((AgentConversationMessage) args[0]);
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (double.class.equals(returnType)) {
            return 0D;
        }
        if (float.class.equals(returnType)) {
            return 0F;
        }
        return 0;
    }
}
