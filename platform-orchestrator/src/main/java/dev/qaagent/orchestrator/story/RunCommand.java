package dev.qaagent.orchestrator.story;

public record RunCommand(String runId, String storyId, String triggeredBy) {
}
