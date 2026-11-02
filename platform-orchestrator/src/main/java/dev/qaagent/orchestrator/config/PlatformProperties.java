package dev.qaagent.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaagent.platform")
public record PlatformProperties(Boolean autoPostStoryComment) {

    public boolean postsStoryComment() {
        return autoPostStoryComment == null || autoPostStoryComment;
    }
}
