package dev.qaagent.orchestrator.story;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface StoryToTestAssetsWorkflow {

    @WorkflowMethod
    void run(RunCommand command);

    @SignalMethod
    void approvalDecision(ApprovalDecision decision);
}
