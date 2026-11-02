package dev.qaagent.integrations.qtest;

import dev.qaagent.core.port.TestManagementPort;
import dev.qaagent.core.testcase.ExternalTestCase;
import dev.qaagent.core.testcase.ExternalTestCaseRef;
import dev.qaagent.core.testcase.TestCaseDraft;
import dev.qaagent.core.testcase.TestCaseStatus;
import dev.qaagent.integrations.audit.AuditService;
import dev.qaagent.integrations.config.QTestProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class QTestAdapter implements TestManagementPort {

    private final QTestClient client;
    private final AuditService audit;

    public QTestAdapter(QTestProperties qtest, AuditService audit) {
        this.client = new QTestClient(
                qtest.baseUrl(),
                qtest.token(),
                qtest.projectId(),
                qtest.moduleId(),
                qtest.statusFieldId());
        this.audit = audit;
    }

    @Override
    public ExternalTestCaseRef createDraftTestCase(String storyId, TestCaseDraft draft) {
        ExternalTestCaseRef ref = client.createDraft(storyId, draft);
        audit.record("createDraftTestCase", "qtest", ref.testCaseId(), Map.of(
                "storyId", storyId,
                "key", draft.key(),
                "status", TestCaseStatus.DRAFT.name(),
                "url", ref.url()));
        return ref;
    }

    @Override
    public void updateTestCase(ExternalTestCaseRef ref, TestCaseDraft draft) {
        TestCaseStatus status = client.statusOf(ref);
        client.update(ref, draft, status);
        audit.record("updateTestCase", "qtest", ref.testCaseId(), Map.of(
                "key", draft.key(),
                "status", status.name()));
    }

    @Override
    public void setStatus(ExternalTestCaseRef ref, TestCaseStatus status) {
        client.setStatus(ref, status);
        audit.record("setStatus", "qtest", ref.testCaseId(), Map.of("status", status.name()));
    }

    @Override
    public void attachNote(ExternalTestCaseRef ref, String text) {
        client.addComment(ref, text);
        audit.record("attachNote", "qtest", ref.testCaseId(), Map.of("text", text));
    }

    @Override
    public List<ExternalTestCase> findByModule(String module) {
        return client.findByModule(module);
    }
}
