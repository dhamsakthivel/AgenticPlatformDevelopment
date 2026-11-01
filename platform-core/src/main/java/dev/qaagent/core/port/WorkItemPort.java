package dev.qaagent.core.port;

import dev.qaagent.core.story.Story;

public interface WorkItemPort {

    Story getStory(String storyId);

    void addComment(String storyId, String markdown);

    void addLink(String storyId, String url, String label);
}
