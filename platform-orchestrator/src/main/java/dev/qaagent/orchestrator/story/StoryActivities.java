package dev.qaagent.orchestrator.story;

import dev.qaagent.core.story.RetrievedChunk;
import dev.qaagent.core.story.Story;
import dev.qaagent.core.story.StoryAnalysisInput;
import dev.qaagent.core.story.StoryAnalysisOutput;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface StoryActivities {

    @ActivityMethod
    Story fetchStory(String runId, String storyId);

    @ActivityMethod
    List<RetrievedChunk> retrieveContext(String runId, Story story);

    @ActivityMethod
    StoryAnalysisOutput analyze(String runId, StoryAnalysisInput input);

    @ActivityMethod
    void postComment(String runId, String storyId, StoryAnalysisOutput output);

    @ActivityMethod
    String markWaiting(String runId);

    @ActivityMethod
    void complete(String runId, String awaitStepId, boolean signaled, ApprovalDecision decision, int riskScore);

    @ActivityMethod
    void failRun(String runId, String error);
}
