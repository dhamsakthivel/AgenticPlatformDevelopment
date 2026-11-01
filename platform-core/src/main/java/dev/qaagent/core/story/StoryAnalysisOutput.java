package dev.qaagent.core.story;

import java.util.List;

public record StoryAnalysisOutput(
        String storyId,
        String summary,
        List<Criterion> criteria,
        List<Ambiguity> ambiguities,
        List<String> missingScenarios,
        int riskScore,
        List<String> riskReasons,
        List<Citation> citations
) {
    public StoryAnalysisOutput {
        criteria = List.copyOf(criteria == null ? List.of() : criteria);
        ambiguities = List.copyOf(ambiguities == null ? List.of() : ambiguities);
        missingScenarios = List.copyOf(missingScenarios == null ? List.of() : missingScenarios);
        riskReasons = List.copyOf(riskReasons == null ? List.of() : riskReasons);
        citations = List.copyOf(citations == null ? List.of() : citations);
    }

    public record Criterion(String id, String text, boolean testable, String issue) {
    }

    public record Ambiguity(String question, Severity severity) {
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }

    public record Citation(String chunkId, String source) {
    }
}
