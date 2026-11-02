package dev.qaagent.core.testcase;

public record ExternalTestCase(
        ExternalTestCaseRef ref,
        String module,
        TestCaseStatus status,
        TestCaseDraft draft
) {
}
