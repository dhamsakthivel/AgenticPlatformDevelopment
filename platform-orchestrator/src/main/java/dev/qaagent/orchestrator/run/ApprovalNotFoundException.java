package dev.qaagent.orchestrator.run;

public class ApprovalNotFoundException extends RuntimeException {

    public ApprovalNotFoundException(String approvalId) {
        super("Approval not found: " + approvalId);
    }
}
