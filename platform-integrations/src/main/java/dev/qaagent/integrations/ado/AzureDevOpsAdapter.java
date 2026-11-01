package dev.qaagent.integrations.ado;

import dev.qaagent.core.port.WorkItemPort;
import dev.qaagent.core.story.Story;
import dev.qaagent.integrations.audit.AuditService;
import dev.qaagent.integrations.config.AdoProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("sandbox")
public class AzureDevOpsAdapter implements WorkItemPort {

    private final AdoWorkItemClient client;
    private final AuditService audit;

    public AzureDevOpsAdapter(AdoProperties ado, AuditService audit) {
        if (ado.pat() == null || ado.pat().isBlank()) {
            throw new IllegalStateException("ADO_PAT is empty. The sandbox profile cannot call Azure DevOps without it.");
        }
        this.client = new AdoWorkItemClient(ado.orgUrl(), ado.project(), ado.pat());
        this.audit = audit;
    }

    @Override
    public Story getStory(String storyId) {
        return client.getStory(storyId);
    }

    @Override
    public void addComment(String storyId, String markdown) {
        client.addComment(storyId, markdown);
        audit.record("addComment", "azure-devops", storyId, Map.of("mode", "sandbox"));
    }

    @Override
    public void addLink(String storyId, String url, String label) {
        client.addLink(storyId, url, label);
        audit.record("addLink", "azure-devops", storyId, Map.of(
                "mode", "sandbox",
                "url", url,
                "label", label));
    }
}
