package dev.qaagent.core.llm;

public record LlmRequest(
        String purpose,
        String promptId,
        String promptVersion,
        String content,
        String jsonSchema
) {
}
