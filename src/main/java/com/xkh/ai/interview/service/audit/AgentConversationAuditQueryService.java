package com.xkh.ai.interview.service.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xkh.ai.interview.entity.AgentConversationMessageEntity;
import com.xkh.ai.interview.mapper.AgentConversationMessageMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentConversationAuditQueryService {

    private final AgentConversationMessageMapper agentConversationMessageMapper;

    public AgentConversationAuditQueryService(AgentConversationMessageMapper agentConversationMessageMapper) {
        this.agentConversationMessageMapper = agentConversationMessageMapper;
    }

    public List<AgentConversationMessageEntity> listRecent(String conversationId, String traceId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        LambdaQueryWrapper<AgentConversationMessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(conversationId), AgentConversationMessageEntity::getConversationId, conversationId);
        wrapper.eq(StringUtils.isNotBlank(traceId), AgentConversationMessageEntity::getTraceId, traceId);
        wrapper.orderByDesc(AgentConversationMessageEntity::getCreateTime);
        wrapper.last("LIMIT " + safeLimit);
        return agentConversationMessageMapper.selectList(wrapper);
    }
}

