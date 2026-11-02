package dev.qaagent.orchestrator.run;

import dev.qaagent.orchestrator.story.ApprovalDecision;
import dev.qaagent.orchestrator.story.StoryToTestAssetsWorkflow;
import io.temporal.client.WorkflowClient;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ApprovalService {

    private final RunStore runs;
    private final WorkflowClient workflows;

    public ApprovalService(RunStore runs, WorkflowClient workflows) {
        this.runs = runs;
        this.workflows = workflows;
    }

    public List<RunStore.ApprovalView> list(String status) {
        return runs.listApprovals(status);
    }

    public RunStore.ApprovalView decide(UUID approvalId, ApprovalDecision decision) {
        RunStore.ApprovalView approval = runs.findApproval(approvalId)
                .orElseThrow(() -> new ApprovalNotFoundException(approvalId.toString()));
        if (!"PENDING".equals(approval.status())) {
            throw new ApprovalClosedException("This approval is already " + approval.status() + ".");
        }
        validate(approval, decision);
        try {
            StoryToTestAssetsWorkflow workflow = workflows.newWorkflowStub(
                    StoryToTestAssetsWorkflow.class, approval.runId().toString());
            workflow.approvalDecision(decision);
        } catch (ApprovalClosedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApprovalClosedException("The run is no longer waiting for approval.");
        }
        return approval;
    }

    private static void validate(RunStore.ApprovalView approval, ApprovalDecision decision) {
        if (decision == null || decision.decidedBy() == null || decision.decidedBy().isBlank()) {
            throw new IllegalArgumentException("Enter who decided, for example sam.");
        }
        if (decision.decisions().isEmpty()) {
            throw new IllegalArgumentException("Choose approve, reject, or edit for each draft.");
        }
        if (approval.drafts().isEmpty()) {
            throw new IllegalArgumentException("This run has no drafts to decide.");
        }
        Set<String> expected = new HashSet<>();
        for (RunStore.DraftView draft : approval.drafts()) {
            expected.add(draft.key());
        }
        Set<String> seen = new HashSet<>();
        for (ApprovalDecision.CaseDecision item : decision.decisions()) {
            if (item.key() == null || item.key().isBlank() || item.action() == null) {
                throw new IllegalArgumentException("Each decision needs a draft key and an action.");
            }
            if (!expected.contains(item.key())) {
                throw new IllegalArgumentException("Draft " + item.key() + " is not part of this approval.");
            }
            if (!seen.add(item.key())) {
                throw new IllegalArgumentException("Draft " + item.key() + " was decided more than once.");
            }
            if (item.action() == ApprovalDecision.Action.EDIT && item.editedCase() == null) {
                throw new IllegalArgumentException("An edit of " + item.key() + " needs the replacement case.");
            }
        }
        if (!seen.equals(expected)) {
            expected.removeAll(seen);
            throw new IllegalArgumentException("Decide every draft. Missing " + String.join(", ", expected) + ".");
        }
    }
}
