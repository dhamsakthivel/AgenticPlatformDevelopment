package dev.qaagent.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaagent.openai")
public record OpenAiEmbeddingSettings(String apiKey, String embeddingModel, Integer embeddingDimensions) {

    public int dimensions() {
        return embeddingDimensions == null || embeddingDimensions < 1 ? 1536 : embeddingDimensions;
    }

    public String model() {
        return embeddingModel == null || embeddingModel.isBlank() ? "text-embedding-3-small" : embeddingModel;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
