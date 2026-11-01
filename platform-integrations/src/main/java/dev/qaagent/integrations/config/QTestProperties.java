package dev.qaagent.integrations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaagent.qtest")
public record QTestProperties(String baseUrl, String token, String projectId, String moduleId, String statusFieldId) {
}
