package com.xkh.ai.interview;

import com.xkh.ai.interview.support.PromptVersionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptVersionRegistryTests {

    @Test
    void returnsConfiguredPromptVersion() {
        PromptVersionRegistry registry = new PromptVersionRegistry();
        registry.setVersions(Map.of("jd-match", "jd-match-v1"));

        assertEquals("jd-match-v1", registry.versionOf("jd-match"));
    }

    @Test
    void returnsUnknownWhenOperationIsMissing() {
        PromptVersionRegistry registry = new PromptVersionRegistry();

        assertEquals("unknown", registry.versionOf("missing-operation"));
    }
}
