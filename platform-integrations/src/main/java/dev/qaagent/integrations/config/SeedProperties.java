package dev.qaagent.integrations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaagent.seed")
public record SeedProperties(String storiesPath) {
}
