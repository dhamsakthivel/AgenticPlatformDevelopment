package dev.qaagent.integrations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaagent.ado")
public record AdoProperties(String orgUrl, String project, String pat, String webhookSecret) {
}
