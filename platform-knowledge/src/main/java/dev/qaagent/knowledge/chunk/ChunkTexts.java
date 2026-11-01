package dev.qaagent.knowledge.chunk;

import dev.qaagent.core.story.Story;
import dev.qaagent.core.testcase.ExternalTestCase;
import dev.qaagent.core.testcase.TestCaseDraft;
import dev.qaagent.core.testcase.TestCaseStep;

/**
 * A story and a test case are each stored as one chunk, even when that chunk is short.
 */
public final class ChunkTexts {

    private ChunkTexts() {
    }

    public static String story(Story story) {
        StringBuilder text = new StringBuilder();
        text.append("Story ").append(story.id()).append(": ").append(story.title()).append('\n');
        text.append("State: ").append(story.state()).append('\n');
        text.append("Module: ").append(story.module()).append("\n\n");
        text.append(story.description()).append("\n\nAcceptance criteria:\n");
        if (story.acceptanceCriteria().isEmpty()) {
            text.append("- (none listed)\n");
        } else {
            for (String criterion : story.acceptanceCriteria()) {
                text.append("- ").append(criterion).append('\n');
            }
        }
        return text.toString().trim();
    }

    public static String testCase(ExternalTestCase testCase) {
        TestCaseDraft draft = testCase.draft();
        StringBuilder text = new StringBuilder();
        text.append("Test case ").append(draft.key()).append(": ").append(draft.title()).append('\n');
        text.append("Status: ").append(testCase.status()).append('\n');
        text.append("Module: ").append(testCase.module()).append('\n');
        text.append("Type: ").append(draft.type()).append('\n');
        text.append("Priority: ").append(draft.priority()).append("\n\n");
        text.append("Preconditions:\n");
        appendList(text, draft.preconditions());
        text.append("\nSteps:\n");
        if (draft.steps().isEmpty()) {
            text.append("- (none listed)\n");
        } else {
            int order = 1;
            for (TestCaseStep step : draft.steps()) {
                text.append(order++).append(". ").append(step.action()).append(" -> ").append(step.expected()).append('\n');
            }
        }
        text.append("\nData:\n");
        appendList(text, draft.dataRequirements());
        text.append("\nLinked criteria:\n");
        appendList(text, draft.linkedCriteria());
        return text.toString().trim();
    }

    private static void appendList(StringBuilder text, java.util.List<String> items) {
        if (items.isEmpty()) {
            text.append("- (none listed)\n");
            return;
        }
        for (String item : items) {
            text.append("- ").append(item).append('\n');
        }
    }
}
