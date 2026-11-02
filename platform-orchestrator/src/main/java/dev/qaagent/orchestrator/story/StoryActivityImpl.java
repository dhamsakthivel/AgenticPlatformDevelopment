package dev.qaagent.orchestrator.story;

import dev.qaagent.agents.story.StoryAnalysisAgent;
import dev.qaagent.agents.story.StoryAnalysisComment;
import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.core.port.WorkItemPort;
import dev.qaagent.core.story.RetrievedChunk;
import dev.qaagent.core.story.Story;
import dev.qaagent.core.story.StoryAnalysisInput;
import dev.qaagent.core.story.StoryAnalysisOutput;
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
    private final StoryAnalysisComment comment;
    private final PlatformProperties platform;
    private final RunStore runs;

    public StoryActivityImpl(WorkItemPort stories, KnowledgeService knowledge, StoryAnalysisAgent analyst,
                             PlatformProperties platform, RunStore runs) {
        this.stories = stories;
        this.knowledge = knowledge;
        this.analyst = analyst;
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
        runs.finishStep(UUID.fromString(awaitStepId), Map.of(
                "signaled", signaled,
                "decidedBy", decidedBy,
                "note", decision == null || decision.note() == null ? "" : decision.note()));
        runs.completeRun(UUID.fromString(runId), signaled, decidedBy, riskScore);
    }

    @Override
    public void failRun(String runId, String error) {
        runs.failRun(UUID.fromString(runId), error);
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
}
