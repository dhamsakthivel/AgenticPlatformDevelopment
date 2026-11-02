package dev.qaagent.core.design;

import dev.qaagent.core.story.RetrievedChunk;
import dev.qaagent.core.story.StoryAnalysisOutput;

import java.util.List;

public record TestDesignInput(
        String storyId,
        String title,
        String description,
        List<String> acceptanceCriteria,
        StoryAnalysisOutput analysis,
        List<RetrievedChunk> retrievedContext,
        List<SimilarCase> similarCases
) {
    public TestDesignInput {
        acceptanceCriteria = List.copyOf(acceptanceCriteria == null ? List.of() : acceptanceCriteria);
        retrievedContext = List.copyOf(retrievedContext == null ? List.of() : retrievedContext);
        similarCases = List.copyOf(similarCases == null ? List.of() : similarCases);
    }

    public record SimilarCase(String id, String title, String summary) {
    }
}
