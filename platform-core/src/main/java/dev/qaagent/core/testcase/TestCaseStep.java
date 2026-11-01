package dev.qaagent.core.testcase;

public record TestCaseStep(String action, String expected) {
    public TestCaseStep {
        action = action == null ? "" : action;
        expected = expected == null ? "" : expected;
    }
}
