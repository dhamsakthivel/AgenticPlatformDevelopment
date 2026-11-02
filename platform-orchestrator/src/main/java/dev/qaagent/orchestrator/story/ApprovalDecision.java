package dev.qaagent.orchestrator.story;

import dev.qaagent.core.testcase.TestCaseDraft;

import java.util.List;

public record ApprovalDecision(String decidedBy, List<CaseDecision> decisions) {

    public ApprovalDecision {
        decisions = List.copyOf(decisions == null ? List.of() : decisions);
    }

    public record CaseDecision(String key, Action action, TestCaseDraft editedCase) {
    }

    public enum Action {
        APPROVE,
        REJECT,
        EDIT
    }
}
