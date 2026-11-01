package dev.qaagent.agents.prompt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "qaagent.prompts")
public record PromptProperties(Map<String, String> active) {

    public PromptProperties {
        active = active == null ? Map.of() : Map.copyOf(active);
    }

    public String version(String agent) {
        if (agent == null || agent.isBlank()) {
            return "";
        }
        String version = active.get(agent);
        return version == null ? "" : version;
    }
}
