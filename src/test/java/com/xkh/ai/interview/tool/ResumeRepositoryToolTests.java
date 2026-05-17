package com.xkh.ai.interview.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xkh.ai.interview.entity.ResumeInfo;
import com.xkh.ai.interview.mapper.ResumeInfoMapper;
import com.xkh.ai.interview.service.dto.ResumeData;
import com.xkh.ai.interview.service.dto.ResumeScoreResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResumeRepositoryToolTests {

    @Test
    void findByIdFallsBackToDatabaseWhenCacheReadFails() {
        ResumeInfo entity = new ResumeInfo();
        entity.setResumeId("resume-001");
        entity.setResumeText("Java backend resume");
        AtomicReference<Object> selectedId = new AtomicReference<>();
        ResumeInfoMapper mapper = mapperReturning(entity, selectedId, new AtomicReference<>());
        RedisTemplate<String, Object> redisTemplate = new StubRedisTemplate(valueOperations(true, false));
        ResumeRepositoryTool repositoryTool = new ResumeRepositoryTool(mapper, redisTemplate, new ObjectMapper());

        ResumeData data = repositoryTool.findById("resume-001");

        assertEquals("resume-001", data.getResumeId());
        assertEquals("Java backend resume", data.getResumeText());
        assertEquals("resume-001", selectedId.get());
    }

    @Test
    void saveAnalyzedResumeDoesNotFailWhenCacheWriteFails() {
        AtomicReference<ResumeInfo> inserted = new AtomicReference<>();
        ResumeInfoMapper mapper = mapperReturning(null, new AtomicReference<>(), inserted);
        RedisTemplate<String, Object> redisTemplate = new StubRedisTemplate(valueOperations(false, true));
        ResumeRepositoryTool repositoryTool = new ResumeRepositoryTool(mapper, redisTemplate, new ObjectMapper());

        assertDoesNotThrow(() -> repositoryTool.saveAnalyzedResume(
                "resume-001",
                "resume.pdf",
                "Java backend resume",
                scoreResult()
        ));
        assertNotNull(inserted.get());
        assertEquals("resume-001", inserted.get().getResumeId());
    }

    private ResumeScoreResult scoreResult() {
        return ResumeScoreResult.builder()
                .overallScore(80)
                .scoreDetail(new ResumeScoreResult.ScoreDetail(30, 20, 10, 10, 10))
                .summary("summary")
                .strengths(List.of("Java"))
                .suggestions(List.of(new ResumeScoreResult.Suggestion("项目", "中", "issue", "recommendation")))
                .build();
    }

    private ResumeInfoMapper mapperReturning(ResumeInfo selectedEntity,
                                             AtomicReference<Object> selectedId,
                                             AtomicReference<ResumeInfo> inserted) {
        return (ResumeInfoMapper) Proxy.newProxyInstance(
                ResumeInfoMapper.class.getClassLoader(),
                new Class<?>[]{ResumeInfoMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        selectedId.set(args[0]);
                        return selectedEntity;
                    }
                    if ("insert".equals(method.getName())) {
                        inserted.set((ResumeInfo) args[0]);
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> valueOperations(boolean failGet, boolean failSet) {
        return (ValueOperations<String, Object>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    if ("get".equals(method.getName()) && failGet) {
                        throw new RuntimeException("redis down");
                    }
                    if ("set".equals(method.getName()) && failSet) {
                        throw new RuntimeException("redis down");
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

    private static class StubRedisTemplate extends RedisTemplate<String, Object> {

        private final ValueOperations<String, Object> valueOperations;

        private StubRedisTemplate(ValueOperations<String, Object> valueOperations) {
            this.valueOperations = valueOperations;
        }

        @Override
        public ValueOperations<String, Object> opsForValue() {
            return valueOperations;
        }
    }
}
