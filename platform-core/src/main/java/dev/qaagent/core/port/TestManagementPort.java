package dev.qaagent.core.port;

import dev.qaagent.core.testcase.ExternalTestCase;
import dev.qaagent.core.testcase.ExternalTestCaseRef;
import dev.qaagent.core.testcase.TestCaseDraft;
import dev.qaagent.core.testcase.TestCaseStatus;

import java.util.List;

public interface TestManagementPort {

    ExternalTestCaseRef createDraftTestCase(String storyId, TestCaseDraft draft);

    void updateTestCase(ExternalTestCaseRef ref, TestCaseDraft draft);

    void setStatus(ExternalTestCaseRef ref, TestCaseStatus status);

    void attachNote(ExternalTestCaseRef ref, String text);

    List<ExternalTestCase> findByModule(String module);
}
