package dev.qaagent.core.testdata;

import java.util.List;

public record TestDataSet(
        String id,
        String testCaseKey,
        List<String> files,
        List<String> members,
        String expectedOutcome
) {
    public TestDataSet {
        files = List.copyOf(files == null ? List.of() : files);
        members = List.copyOf(members == null ? List.of() : members);
    }
}
