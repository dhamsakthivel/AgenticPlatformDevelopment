package dev.qaagent.core;

public class StoryNotFoundException extends RuntimeException {

    public StoryNotFoundException(String storyId) {
        super("Story not found: " + storyId);
    }
}
