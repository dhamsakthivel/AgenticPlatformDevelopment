package dev.qaagent.orchestrator.run;

public record StepEvent(String runId, String stepId, String stepName, String status, String error) {
}
