package dev.qaagent.orchestrator.run;

import dev.qaagent.core.port.WorkItemPort;
import dev.qaagent.orchestrator.config.TemporalProperties;
import dev.qaagent.orchestrator.story.RunCommand;
import dev.qaagent.orchestrator.story.StoryToTestAssetsWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RunService {

    private final RunStore runs;
    private final WorkItemPort stories;
    private final WorkflowClient workflows;
    private final TemporalProperties temporal;

    public RunService(RunStore runs, WorkItemPort stories, WorkflowClient workflows, TemporalProperties temporal) {
        this.runs = runs;
        this.stories = stories;
        this.workflows = workflows;
        this.temporal = temporal;
    }

    public RunStore.RunDetails start(String storyId, String triggeredBy) {
        if (storyId == null || storyId.isBlank()) {
            throw new IllegalArgumentException("Enter a story id, for example ENR-001.");
        }
        String id = storyId.trim();
        stories.getStory(id);
        runs.activeRun(id).ifPresent(existing -> {
            throw new RunAlreadyStartedException(existing, id);
        });
        UUID runId = UUID.randomUUID();
        String actor = triggeredBy == null || triggeredBy.isBlank() ? "api" : triggeredBy.trim();
        runs.insertRun(runId, id, actor);
        try {
            StoryToTestAssetsWorkflow workflow = workflows.newWorkflowStub(
                    StoryToTestAssetsWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(temporal.taskQueue())
                            .setWorkflowId(runId.toString())
                            .build());
            WorkflowClient.start(workflow::run, new RunCommand(runId.toString(), id, actor));
        } catch (RuntimeException exception) {
            runs.failRun(runId, exception.getMessage());
            throw exception;
        }
        return runs.find(runId).orElseThrow(() -> new RunNotFoundException(runId.toString()));
    }

    public RunStore.RunDetails get(UUID runId) {
        return runs.find(runId).orElseThrow(() -> new RunNotFoundException(runId.toString()));
    }

    public List<RunStore.RunDetails> list(String status) {
        return runs.list(status);
    }
}
