package dev.qaagent.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaagent.knowledge")
public record KnowledgeProperties(
        String seedPath,
        String storiesPath,
        int embeddingBatchSize,
        double embeddingUsdPerMillionTokens
) {
}
