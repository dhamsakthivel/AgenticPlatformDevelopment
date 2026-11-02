package dev.qaagent.integrations.workitem;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedStory(
        String id,
        String title,
        String description,
        List<String> acceptanceCriteria,
        String state,
        String module,
        boolean vague
) {
}
