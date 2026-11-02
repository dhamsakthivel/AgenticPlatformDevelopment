package dev.qaagent.core.testcase;

import java.util.List;

public record TestCaseDraft(
        String key,
        String title,
        Type type,
        Priority priority,
        List<String> preconditions,
        List<TestCaseStep> steps,
        List<String> dataRequirements,
        List<String> linkedCriteria
) {
    public enum Type {
        POSITIVE,
        NEGATIVE,
        BOUNDARY
    }

    public enum Priority {
        P1,
        P2,
        P3
    }

    public TestCaseDraft {
        preconditions = List.copyOf(preconditions == null ? List.of() : preconditions);
        steps = List.copyOf(steps == null ? List.of() : steps);
        dataRequirements = List.copyOf(dataRequirements == null ? List.of() : dataRequirements);
        linkedCriteria = List.copyOf(linkedCriteria == null ? List.of() : linkedCriteria);
    }
}
