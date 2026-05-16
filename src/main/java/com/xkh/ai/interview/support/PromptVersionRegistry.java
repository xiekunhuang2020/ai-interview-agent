package com.xkh.ai.interview.support;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "ai-interview.prompt")
public class PromptVersionRegistry {

    private static final String UNKNOWN_VERSION = "unknown";

    private Map<String, String> versions = new HashMap<>();

    public String versionOf(String operationName) {
        return versions.getOrDefault(operationName, UNKNOWN_VERSION);
    }

    public Map<String, String> getVersions() {
        return versions;
    }

    public void setVersions(Map<String, String> versions) {
        this.versions = versions == null ? new HashMap<>() : versions;
    }
}
