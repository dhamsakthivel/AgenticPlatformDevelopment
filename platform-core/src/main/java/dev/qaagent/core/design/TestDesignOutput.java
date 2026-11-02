package dev.qaagent.core.design;

import dev.qaagent.core.testcase.TestCaseDraft;
import dev.qaagent.core.testcase.TestCaseStep;

import java.util.List;

public record TestDesignOutput(String storyId, List<DesignedCase> testCases) {

    public TestDesignOutput {
        testCases = List.copyOf(testCases == null ? List.of() : testCases);
    }

    public record DesignedCase(
            String key,
            String title,
            TestCaseDraft.Type type,
            TestCaseDraft.Priority priority,
            List<String> preconditions,
            List<TestCaseStep> steps,
            List<String> dataRequirements,
            List<String> linkedCriteria,
            String possibleDuplicateOf
    ) {
        public DesignedCase {
            preconditions = List.copyOf(preconditions == null ? List.of() : preconditions);
            steps = List.copyOf(steps == null ? List.of() : steps);
            dataRequirements = List.copyOf(dataRequirements == null ? List.of() : dataRequirements);
            linkedCriteria = List.copyOf(linkedCriteria == null ? List.of() : linkedCriteria);
            if (possibleDuplicateOf != null && possibleDuplicateOf.isBlank()) {
                possibleDuplicateOf = null;
            }
        }

        public TestCaseDraft toDraft() {
            return new TestCaseDraft(key, title, type, priority, preconditions, steps, dataRequirements, linkedCriteria);
        }
    }
}
