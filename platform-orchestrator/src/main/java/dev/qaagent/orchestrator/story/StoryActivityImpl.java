package dev.qaagent.orchestrator.story;

import dev.qaagent.agents.design.TestDesignAgent;
import dev.qaagent.agents.story.StoryAnalysisAgent;
import dev.qaagent.agents.story.StoryAnalysisComment;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.core.design.TestDesignInput;
import dev.qaagent.core.design.TestDesignOutput;
import dev.qaagent.core.port.TestManagementPort;
import dev.qaagent.core.port.WorkItemPort;
import dev.qaagent.core.story.RetrievedChunk;
import dev.qaagent.core.story.Story;
import dev.qaagent.core.story.StoryAnalysisInput;
import dev.qaagent.core.story.StoryAnalysisOutput;
import dev.qaagent.core.testcase.ExternalTestCase;
import dev.qaagent.core.testcase.ExternalTestCaseRef;
import dev.qaagent.core.testcase.TestCaseDraft;
import dev.qaagent.core.testcase.TestCaseStatus;
import dev.qaagent.knowledge.ingest.KnowledgeService;
import dev.qaagent.knowledge.retrieve.RankedChunk;
import dev.qaagent.orchestrator.config.PlatformProperties;
import dev.qaagent.orchestrator.run.RunStore;
import io.temporal.failure.ApplicationFailure;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StoryActivityImpl implements StoryActivities {

    private final WorkItemPort stories;
    private final KnowledgeService knowledge;
    private final StoryAnalysisAgent analyst;
    private final TestDesignAgent designer;
    private final TestManagementPort testCases;
    private final StoryAnalysisComment comment;
    private final PlatformProperties platform;
    private final RunStore runs;

    public StoryActivityImpl(WorkItemPort stories, KnowledgeService knowledge, StoryAnalysisAgent analyst,
                             TestDesignAgent designer, TestManagementPort testCases,
                             PlatformProperties platform, RunStore runs) {
        this.stories = stories;
        this.knowledge = knowledge;
        this.analyst = analyst;
        this.designer = designer;
        this.testCases = testCases;
        this.comment = new StoryAnalysisComment();
        this.platform = platform;
        this.runs = runs;
    }

    @Override
    public Story fetchStory(String runId, String storyId) {
        return step(runId, "fetchStory", null, Map.of("storyId", storyId), () -> stories.getStory(storyId));
    }

    @Override
    public List<RetrievedChunk> retrieveContext(String runId, Story story) {
        return step(runId, "retrieveContext", "story-analysis", Map.of("storyId", story.id()), () -> {
            List<RankedChunk> hits = knowledge.search(
                    story.title() + "\n" + story.description(),
                    null, null, null, "story-analysis", null);
            List<RetrievedChunk> context = new ArrayList<>();
            for (RankedChunk hit : hits) {
                context.add(new RetrievedChunk(hit.chunkId().toString(), hit.source(), hit.title(), hit.text()));
            }
            return context;
        });
    }

    @Override
    public StoryAnalysisOutput analyze(String runId, StoryAnalysisInput input) {
        return step(runId, "runStoryAnalysis", "story-analysis", Map.of("storyId", input.storyId()),
                () -> analyst.analyze(input));
    }

    @Override
    public void postComment(String runId, String storyId, StoryAnalysisOutput output) {
        step(runId, "postStoryComment", "story-analysis", Map.of("storyId", storyId), () -> {
            String markdown = comment.toMarkdown(output);
            String key = runId + ":postStoryComment:" + storyId;
            boolean posted = false;
            if (platform.postsStoryComment() && !runs.alreadyPosted(key)) {
                stories.addComment(storyId, markdown);
                posted = true;
            }
            runs.rememberPost(UUID.fromString(runId), storyId, key, posted);
            return Map.of("posted", posted, "markdown", markdown);
        });
    }

    @Override
    public String markWaiting(String runId) {
        UUID id = UUID.fromString(runId);
        runs.updateStatus(id, "WAITING_FOR_APPROVAL");
        UUID stepId = runs.startStep(id, "awaitApproval", null, Map.of("timeoutDays", 7));
        return stepId.toString();
    }

    @Override
    public void complete(String runId, String awaitStepId, boolean signaled, ApprovalDecision decision, int riskScore) {
        String decidedBy = decision == null || decision.decidedBy() == null ? "" : decision.decidedBy();
        runs.finishStep(UUID.fromString(awaitStepId), Map.of("signaled", signaled, "decidedBy", decidedBy));
        runs.completeRun(UUID.fromString(runId), signaled, decidedBy, riskScore, 0, 0, 0);
    }

    @Override
    public TestDesignOutput runTestDesign(String runId, Story story, StoryAnalysisOutput analysis) {
        return step(runId, "runTestDesign", "test-design", Map.of("storyId", story.id()), () -> {
            List<RankedChunk> hits = knowledge.search(
                    story.title() + "\n" + story.description(),
                    null, null, null, "test-design", null);
            List<RetrievedChunk> context = new ArrayList<>();
            for (RankedChunk hit : hits) {
                context.add(new RetrievedChunk(hit.chunkId().toString(), hit.source(), hit.title(), hit.text()));
            }
            return designer.design(new TestDesignInput(
                    story.id(), story.title(), story.description(), story.acceptanceCriteria(),
                    analysis, context, similarCases()));
        });
    }

    @Override
    public void saveDrafts(String runId, String storyId, TestDesignOutput designed) {
        step(runId, "saveDraftTestCases", "test-design", Map.of("storyId", storyId), () -> {
            List<Map<String, String>> saved = new ArrayList<>();
            UUID id = UUID.fromString(runId);
            for (TestDesignOutput.DesignedCase item : designed.testCases()) {
                String idempotencyKey = runId + ":saveDraftTestCases:" + item.key();
                var existing = runs.findDraft(id, item.key());
                if (existing.isPresent()) {
                    saved.add(Map.of(
                            "key", item.key(),
                            "testCaseId", existing.get().externalRef() == null ? "" : existing.get().externalRef(),
                            "status", existing.get().status()));
                    continue;
                }
                if (runs.alreadyRecorded("saveDraftTestCase", idempotencyKey)) {
                    saved.add(Map.of("key", item.key(), "testCaseId", "", "status", "DRAFT"));
                    continue;
                }
                var ref = testCases.createDraftTestCase(storyId, item.toDraft());
                runs.insertDraft(id, item.key(), ref.testCaseId(), item);
                runs.rememberAction(id, "saveDraftTestCase", "qtest", ref.testCaseId(),
                        Map.of("idempotencyKey", idempotencyKey, "key", item.key()));
                saved.add(Map.of("key", item.key(), "testCaseId", ref.testCaseId(), "status", "DRAFT"));
            }
            return saved;
        });
    }

    @Override
    public String createApprovalRequest(String runId) {
        UUID id = UUID.fromString(runId);
        UUID approvalId = runs.pendingApproval(id).orElseGet(() -> runs.insertApproval(id));
        runs.updateStatus(id, "WAITING_FOR_APPROVAL");
        UUID stepId = runs.startStep(id, "awaitApproval", null, Map.of(
                "approvalId", approvalId.toString(),
                "timeoutDays", 7));
        return stepId.toString();
    }

    @Override
    public void applyDecision(String runId, String awaitStepId, boolean signaled, ApprovalDecision decision, int riskScore) {
        String decidedBy = decision == null || decision.decidedBy() == null ? "" : decision.decidedBy();
        int approved = 0;
        int rejected = 0;
        int edited = 0;
        UUID id = UUID.fromString(runId);
        if (signaled && decision != null) {
            for (ApprovalDecision.CaseDecision item : decision.decisions()) {
                Outcome outcome = applyOne(id, runId, item);
                approved += outcome.approved();
                rejected += outcome.rejected();
                edited += outcome.edited();
            }
        }
        String approvalStatus = signaled ? "DECIDED" : "TIMED_OUT";
        runs.decideApproval(id, approvalStatus, decidedBy, decision);
        runs.finishStep(UUID.fromString(awaitStepId), Map.of(
                "signaled", signaled,
                "decidedBy", decidedBy,
                "approved", approved,
                "rejected", rejected,
                "edited", edited));
        runs.completeRun(id, signaled, decidedBy, riskScore, approved, rejected, edited);
    }

    @Override
    public void failRun(String runId, String error) {
        runs.failRun(UUID.fromString(runId), error);
    }

    private Outcome applyOne(UUID runId, String runText, ApprovalDecision.CaseDecision item) {
        if (item == null || item.key() == null || item.action() == null) {
            return Outcome.NONE;
        }
        var draft = runs.findDraft(runId, item.key());
        if (draft.isEmpty() || draft.get().externalRef() == null || draft.get().externalRef().isBlank()) {
            return Outcome.NONE;
        }
        String idempotencyKey = runText + ":applyDecision:" + item.key();
        if (runs.alreadyRecorded("applyDecision", idempotencyKey)) {
            return Outcome.NONE;
        }
        ExternalTestCaseRef ref = new ExternalTestCaseRef(null, draft.get().externalRef(), null);
        Outcome outcome = switch (item.action()) {
            case APPROVE -> {
                testCases.setStatus(ref, TestCaseStatus.APPROVED);
                runs.updateDraft(runId, item.key(), TestCaseStatus.APPROVED.name(), null);
                yield new Outcome(1, 0, 0);
            }
            case REJECT -> {
                testCases.setStatus(ref, TestCaseStatus.REJECTED);
                runs.updateDraft(runId, item.key(), TestCaseStatus.REJECTED.name(), null);
                yield new Outcome(0, 1, 0);
            }
            case EDIT -> {
                TestCaseDraft edited = item.editedCase() == null ? null : item.editedCase();
                if (edited == null) {
                    yield Outcome.NONE;
                }
                testCases.updateTestCase(ref, edited);
                testCases.setStatus(ref, TestCaseStatus.APPROVED);
                runs.updateDraft(runId, item.key(), TestCaseStatus.APPROVED.name(), edited);
                yield new Outcome(0, 0, 1);
            }
        };
        runs.rememberAction(runId, "applyDecision", "qtest", ref.testCaseId(),
                Map.of("idempotencyKey", idempotencyKey, "key", item.key(), "action", item.action().name()));
        return outcome;
    }

    private List<TestDesignInput.SimilarCase> similarCases() {
        try {
            List<TestDesignInput.SimilarCase> similar = new ArrayList<>();
            for (ExternalTestCase existing : testCases.findByModule(null)) {
                String summary = existing.draft().title();
                if (!existing.draft().steps().isEmpty()) {
                    summary = summary + ". " + existing.draft().steps().get(0).expected();
                }
                similar.add(new TestDesignInput.SimilarCase(existing.ref().testCaseId(), existing.draft().title(), summary));
            }
            return similar;
        } catch (IntegrationException exception) {
            return List.of();
        }
    }

    private <T> T step(String runId, String name, String agent, Object input, StepBody<T> body) {
        UUID stepId = runs.startStep(UUID.fromString(runId), name, agent, input);
        MDC.put("runId", runId);
        MDC.put("stepId", stepId.toString());
        try {
            T value = body.run();
            runs.finishStep(stepId, value);
            return value;
        } catch (RuntimeException exception) {
            runs.failStep(stepId, exception.getMessage());
            throw wrap(exception);
        } finally {
            MDC.clear();
        }
    }

    private static RuntimeException wrap(RuntimeException exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (exception instanceof StoryNotFoundException || terminal(message)) {
            return ApplicationFailure.newNonRetryableFailure(message, exception.getClass().getName());
        }
        return exception;
    }

    private static boolean terminal(String message) {
        return message.contains("does not match schema")
                || message.contains("cited chunk")
                || message.contains("Set OPENAI_API_KEY");
    }

    @FunctionalInterface
    private interface StepBody<T> {
        T run();
    }

    private record Outcome(int approved, int rejected, int edited) {
        private static final Outcome NONE = new Outcome(0, 0, 0);
    }
}
