package dev.qaagent.core.story;

import java.util.List;

public record Story(
        String id,
        String title,
        String description,
        List<String> acceptanceCriteria,
        String state,
        String module
) {
    public Story {
        acceptanceCriteria = List.copyOf(acceptanceCriteria == null ? List.of() : acceptanceCriteria);
        state = state == null ? "" : state;
        module = module == null ? "" : module;
    }
}
