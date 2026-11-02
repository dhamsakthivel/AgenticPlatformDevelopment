package dev.qaagent.integrations.workitem;

import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.core.port.WorkItemPort;
import dev.qaagent.core.story.Story;
import dev.qaagent.integrations.audit.AuditService;
import dev.qaagent.integrations.config.SeedProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Profile("local")
public class MockWorkItemAdapter implements WorkItemPort {

    private final AuditService audit;
    private final String storiesPath;
    private Map<String, Story> stories;

    public MockWorkItemAdapter(AuditService audit, SeedProperties seed) {
        this.audit = audit;
        this.storiesPath = seed.storiesPath();
    }

    @Override
    public Story getStory(String storyId) {
        Story story = stories().get(storyId);
        if (story == null) {
            throw new StoryNotFoundException(storyId);
        }
        return story;
    }

    @Override
    public void addComment(String storyId, String markdown) {
        getStory(storyId);
        audit.record("addComment", "azure-devops", storyId, Map.of(
                "mode", "local",
                "markdown", markdown));
    }

    @Override
    public void addLink(String storyId, String url, String label) {
        getStory(storyId);
        audit.record("addLink", "azure-devops", storyId, Map.of(
                "mode", "local",
                "url", url,
                "label", label));
    }

    private synchronized Map<String, Story> stories() {
        if (stories == null) {
            stories = StoryCatalog.load(StoryCatalog.resolve(storiesPath)).stream()
                    .map(StoryCatalog::toStory)
                    .collect(Collectors.toUnmodifiableMap(Story::id, Function.identity()));
        }
        return stories;
    }
}
