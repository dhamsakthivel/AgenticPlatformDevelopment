package dev.qaagent.orchestrator.run;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(String runId) {
        super("Run not found: " + runId);
    }
}
