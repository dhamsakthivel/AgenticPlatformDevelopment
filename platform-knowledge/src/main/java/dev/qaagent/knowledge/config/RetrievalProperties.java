package dev.qaagent.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "qaagent.retrieval")
public record RetrievalProperties(Map<String, Profile> profiles) {

    public RetrievalProperties {
        profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
    }

    public Profile profile(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return profiles.get(name);
    }

    public record Profile(List<String> sources, String app, String module, Integer topK) {

        public Profile {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }

        public int limit() {
            return topK == null || topK < 1 ? 8 : topK;
        }
    }
}
