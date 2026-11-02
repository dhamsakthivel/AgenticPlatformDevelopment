package dev.qaagent.orchestrator.story;

import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.core.design.TestDesignOutput;
import dev.qaagent.core.story.RetrievedChunk;
import dev.qaagent.core.story.Story;
import dev.qaagent.core.story.StoryAnalysisInput;
import dev.qaagent.core.story.StoryAnalysisOutput;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.List;

public class StoryToTestAssetsWorkflowImpl implements StoryToTestAssetsWorkflow {

    static final String TASK_QUEUE = "qa-agents";
    private static final Duration APPROVAL_WAIT = Duration.ofDays(7);

    private ApprovalDecision decision;

    @Override
    public void approvalDecision(ApprovalDecision decision) {
        this.decision = decision;
    }

    @Override
    public void run(RunCommand command) {
        StoryActivities activities = Workflow.newActivityStub(StoryActivities.class, activityOptions());
        try {
            Story story = activities.fetchStory(command.runId(), command.storyId());
            List<RetrievedChunk> context = activities.retrieveContext(command.runId(), story);
            StoryAnalysisOutput output = activities.analyze(command.runId(), new StoryAnalysisInput(
                    story.id(), story.title(), story.description(), story.acceptanceCriteria(), context));
            activities.postComment(command.runId(), command.storyId(), output);
            int version = Workflow.getVersion("test-design-before-approval", Workflow.DEFAULT_VERSION, 1);
            if (version == Workflow.DEFAULT_VERSION) {
                String awaitStepId = activities.markWaiting(command.runId());
                boolean signaled = Workflow.await(APPROVAL_WAIT, () -> decision != null);
                activities.complete(command.runId(), awaitStepId, signaled, decision, output.riskScore());
                return;
            }
            TestDesignOutput designed = activities.runTestDesign(command.runId(), story, output);
            activities.saveDrafts(command.runId(), story.id(), designed);
            String awaitStepId = activities.createApprovalRequest(command.runId());
            boolean signaled = Workflow.await(APPROVAL_WAIT, () -> decision != null);
            activities.applyDecision(command.runId(), awaitStepId, signaled, decision, output.riskScore());
        } catch (ActivityFailure exception) {
            activities.failRun(command.runId(), message(exception));
            throw ApplicationFailure.newNonRetryableFailure(message(exception), "RunFailed");
        }
    }

    private static ActivityOptions activityOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(3))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setDoNotRetry(StoryNotFoundException.class.getName())
                        .build())
                .build();
    }

    private static String message(ActivityFailure exception) {
        Throwable current = exception.getCause();
        while (current != null) {
            if (current instanceof ApplicationFailure failure
                    && failure.getOriginalMessage() != null
                    && !failure.getOriginalMessage().isBlank()) {
                return failure.getOriginalMessage();
            }
            current = current.getCause();
        }
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return exception.getMessage() == null ? "The run failed." : exception.getMessage();
    }
}
