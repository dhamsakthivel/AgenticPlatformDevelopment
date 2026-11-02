package dev.qaagent.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaagent.temporal")
public record TemporalProperties(String target, String taskQueue) {

    public String target() {
        return target == null || target.isBlank() ? "localhost:7233" : target;
    }

    public String taskQueue() {
        return taskQueue == null || taskQueue.isBlank() ? "qa-agents" : taskQueue;
    }
}
