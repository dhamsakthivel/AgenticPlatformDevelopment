package dev.qaagent.agents.story;

import dev.qaagent.core.story.StoryAnalysisOutput;

import java.util.List;

public final class StoryAnalysisComment {

    public String toMarkdown(StoryAnalysisOutput output) {
        StringBuilder text = new StringBuilder();
        text.append("## Story analysis\n\n");
        text.append("**Risk score:** ").append(output.riskScore()).append(" out of 5\n\n");
        text.append("### Summary\n\n").append(blank(output.summary())).append("\n\n");
        text.append("### Acceptance criteria\n\n");
        if (output.criteria().isEmpty()) {
            text.append("- None listed.\n");
        } else {
            for (StoryAnalysisOutput.Criterion criterion : output.criteria()) {
                text.append("- ").append(blank(criterion.id()));
                text.append(criterion.testable() ? " (testable): " : " (not testable): ");
                text.append(blank(criterion.text()));
                if (!criterion.testable() && criterion.issue() != null && !criterion.issue().isBlank()) {
                    text.append(" — ").append(criterion.issue());
                }
                text.append('\n');
            }
        }
        text.append("\n### Ambiguities\n\n");
        if (output.ambiguities().isEmpty()) {
            text.append("- None noted.\n");
        } else {
            for (StoryAnalysisOutput.Ambiguity ambiguity : output.ambiguities()) {
                text.append("- ").append(ambiguity.severity()).append(": ").append(ambiguity.question()).append('\n');
            }
        }
        text.append("\n### Missing scenarios\n\n");
        appendItems(text, output.missingScenarios());
        text.append("\n### Why this risk\n\n");
        appendItems(text, output.riskReasons());
        text.append("\n### Sources\n\n");
        if (output.citations().isEmpty()) {
            text.append("- None.\n");
        } else {
            for (StoryAnalysisOutput.Citation citation : output.citations()) {
                text.append("- ").append(citation.chunkId()).append(" (").append(blank(citation.source())).append(")\n");
            }
        }
        return text.toString().trim() + "\n";
    }

    private static void appendItems(StringBuilder text, List<String> items) {
        if (items.isEmpty()) {
            text.append("- None.\n");
            return;
        }
        for (String item : items) {
            text.append("- ").append(item).append('\n');
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "(blank)" : value;
    }
}
