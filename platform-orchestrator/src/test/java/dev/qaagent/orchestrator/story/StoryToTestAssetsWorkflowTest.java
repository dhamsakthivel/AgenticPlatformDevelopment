package dev.qaagent.orchestrator.story;

import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.core.design.TestDesignOutput;
import dev.qaagent.core.story.RetrievedChunk;
import dev.qaagent.core.testcase.TestCaseDraft;
import dev.qaagent.core.testcase.TestCaseStep;
import dev.qaagent.core.story.Story;
import dev.qaagent.core.story.StoryAnalysisInput;
import dev.qaagent.core.story.StoryAnalysisOutput;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryToTestAssetsWorkflowTest {

    private TestWorkflowEnvironment environment;
    private FakeActivities activities;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        activities = new FakeActivities();
        var worker = environment.newWorker(StoryToTestAssetsWorkflowImpl.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(StoryToTestAssetsWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        environment.start();
        client = environment.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @Test
    void runsTheReviewThenCompletesWhenApprovalIsSignalled() {
        StoryToTestAssetsWorkflow workflow = start("run-1");
        workflow.approvalDecision(new ApprovalDecision("sam", List.of(
                new ApprovalDecision.CaseDecision("TC1", ApprovalDecision.Action.APPROVE, null),
                new ApprovalDecision.CaseDecision("TC2", ApprovalDecision.Action.REJECT, null))));
        WorkflowStub.fromTyped(workflow).getResult(Void.class);

        assertThat(activities.calls).containsExactly(
                "fetchStory", "retrieveContext", "runStoryAnalysis", "postStoryComment",
                "runTestDesign", "saveDrafts", "createApprovalRequest", "applyDecision");
        assertThat(activities.signaled).isTrue();
        assertThat(activities.decidedBy).isEqualTo("sam");
        assertThat(activities.actions).containsExactly("APPROVE", "REJECT");
        assertThat(activities.riskScore).isEqualTo(2);
    }

    @Test
    void completesWhenTheApprovalWaitTimesOut() {
        StoryToTestAssetsWorkflow workflow = start("run-2");
        WorkflowStub.fromTyped(workflow).getResult(Void.class);

        assertThat(activities.calls).endsWith("applyDecision");
        assertThat(activities.signaled).isFalse();
    }

    @Test
    void stopsWhenTheStoryCannotBeRead() {
        activities.missing = true;
        StoryToTestAssetsWorkflow workflow = start("run-3");

        assertThatThrownBy(() -> WorkflowStub.fromTyped(workflow).getResult(Void.class))
                .isInstanceOf(WorkflowFailedException.class);
        assertThat(activities.calls).containsExactly("fetchStory", "failRun");
        assertThat(activities.failure).contains("ENR-001");
    }

    private StoryToTestAssetsWorkflow start(String runId) {
        StoryToTestAssetsWorkflow workflow = client.newWorkflowStub(
                StoryToTestAssetsWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(StoryToTestAssetsWorkflowImpl.TASK_QUEUE)
                        .setWorkflowId(runId)
                        .build());
        WorkflowClient.start(workflow::run, new RunCommand(runId, "ENR-001", "test"));
        return workflow;
    }

    static final class FakeActivities implements StoryActivities {

        final List<String> calls = new CopyOnWriteArrayList<>();
        boolean missing;
        boolean signaled;
        String decidedBy = "";
        final List<String> actions = new CopyOnWriteArrayList<>();
        int riskScore;
        String failure = "";

        @Override
        public Story fetchStory(String runId, String storyId) {
            calls.add("fetchStory");
            if (missing) {
                throw new StoryNotFoundException(storyId);
            }
            return new Story(storyId, "Add a spouse", "Store the spouse.",
                    List.of("The spouse is stored."), "New", "enrollment");
        }

        @Override
        public List<RetrievedChunk> retrieveContext(String runId, Story story) {
            calls.add("retrieveContext");
            return List.of(new RetrievedChunk("11111111-1111-1111-1111-111111111111",
                    "knowledge-seed", "Spouse rule", "A spouse may be added."));
        }

        @Override
        public StoryAnalysisOutput analyze(String runId, StoryAnalysisInput input) {
            calls.add("runStoryAnalysis");
            return new StoryAnalysisOutput(
                    input.storyId(),
                    "Add a spouse.",
                    List.of(new StoryAnalysisOutput.Criterion("AC1", "The spouse is stored.", true, null)),
                    List.of(),
                    List.of(),
                    2,
                    List.of("The outcome is named."),
                    List.of(new StoryAnalysisOutput.Citation("11111111-1111-1111-1111-111111111111", "knowledge-seed")));
        }

        @Override
        public void postComment(String runId, String storyId, StoryAnalysisOutput output) {
            calls.add("postStoryComment");
        }

        @Override
        public TestDesignOutput runTestDesign(String runId, Story story, StoryAnalysisOutput analysis) {
            calls.add("runTestDesign");
            return new TestDesignOutput(story.id(), List.of(new TestDesignOutput.DesignedCase(
                    "TC1", "Add a spouse", TestCaseDraft.Type.POSITIVE, TestCaseDraft.Priority.P2,
                    List.of("An active subscriber."),
                    List.of(new TestCaseStep("Submit an ADD for a spouse.", "The spouse is stored.")),
                    List.of("spouse aged 30"),
                    List.of("AC1"),
                    null)));
        }

        @Override
        public void saveDrafts(String runId, String storyId, TestDesignOutput designed) {
            calls.add("saveDrafts");
        }

        @Override
        public String createApprovalRequest(String runId) {
            calls.add("createApprovalRequest");
            return "await-step";
        }

        @Override
        public void applyDecision(String runId, String awaitStepId, boolean signaled, ApprovalDecision decision, int riskScore) {
            calls.add("applyDecision");
            this.signaled = signaled;
            this.decidedBy = decision == null ? "" : decision.decidedBy();
            this.riskScore = riskScore;
            if (decision != null) {
                for (ApprovalDecision.CaseDecision item : decision.decisions()) {
                    actions.add(item.action().name());
                }
            }
        }

        @Override
        public String markWaiting(String runId) {
            calls.add("markWaiting");
            return "await-step";
        }

        @Override
        public void complete(String runId, String awaitStepId, boolean signaled, ApprovalDecision decision, int riskScore) {
            calls.add("complete");
            this.signaled = signaled;
            this.decidedBy = decision == null ? "" : decision.decidedBy();
            this.riskScore = riskScore;
        }

        @Override
        public void failRun(String runId, String error) {
            calls.add("failRun");
            failure = error == null ? "" : error;
        }
    }
}
