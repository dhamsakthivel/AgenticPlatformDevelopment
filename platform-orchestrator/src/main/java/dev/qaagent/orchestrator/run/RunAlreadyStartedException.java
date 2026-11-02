package dev.qaagent.orchestrator.run;

import java.util.UUID;

public class RunAlreadyStartedException extends RuntimeException {

    private final UUID runId;

    public RunAlreadyStartedException(UUID runId, String storyId) {
        super("A run is already in progress for story " + storyId + ".");
        this.runId = runId;
    }

    public UUID runId() {
        return runId;
    }
}
