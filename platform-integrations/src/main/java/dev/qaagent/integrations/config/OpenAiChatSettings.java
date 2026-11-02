package dev.qaagent.integrations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaagent.openai")
public record OpenAiChatSettings(String apiKey, String chatModel) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasChatModel() {
        return chatModel != null && !chatModel.isBlank();
    }
}
