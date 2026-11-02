package dev.qaagent.core.story;

import java.util.List;

public record StoryAnalysisInput(
        String storyId,
        String title,
        String description,
        List<String> acceptanceCriteria,
        List<RetrievedChunk> retrievedContext
) {
    public StoryAnalysisInput {
        acceptanceCriteria = List.copyOf(acceptanceCriteria == null ? List.of() : acceptanceCriteria);
        retrievedContext = List.copyOf(retrievedContext == null ? List.of() : retrievedContext);
    }
}
