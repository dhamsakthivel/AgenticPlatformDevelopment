package dev.qaagent.integrations.workitem;

import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.integrations.audit.AuditService;
import dev.qaagent.integrations.config.SeedProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class MockWorkItemAdapterTest {

    private final AuditService audit = mock(AuditService.class);

    @Test
    void localWritesAreAuditedAgainstTheSeedFile(@TempDir Path dir) throws Exception {
        Path stories = dir.resolve("stories.json");
        Files.writeString(stories, """
                [{
                  "id": "ENR-001",
                  "title": "Add a spouse",
                  "description": "Store the spouse.",
                  "acceptanceCriteria": ["Spouse is stored"],
                  "state": "New",
                  "module": "enrollment",
                  "vague": false
                }]
                """);
        MockWorkItemAdapter adapter = new MockWorkItemAdapter(audit, new SeedProperties(stories.toString()));

        assertThat(adapter.getStory("ENR-001").title()).isEqualTo("Add a spouse");
        adapter.addComment("ENR-001", "hello");
        adapter.addLink("ENR-001", "http://localhost/tc/1", "TC1");

        verify(audit).record(eq("addComment"), eq("azure-devops"), eq("ENR-001"),
                argThat(details -> "local".equals(details.get("mode")) && "hello".equals(details.get("markdown"))));
        verify(audit).record(eq("addLink"), eq("azure-devops"), eq("ENR-001"),
                argThat(details -> "TC1".equals(details.get("label"))));
        assertThatThrownBy(() -> adapter.getStory("missing")).isInstanceOf(StoryNotFoundException.class);
        verifyNoMoreInteractions(audit);
    }
}
