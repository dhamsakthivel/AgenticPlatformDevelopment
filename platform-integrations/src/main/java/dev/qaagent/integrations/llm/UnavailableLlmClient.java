package dev.qaagent.integrations.llm;

import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.llm.LlmRequest;
import dev.qaagent.core.llm.LlmResult;
import dev.qaagent.core.port.LlmClient;

public final class UnavailableLlmClient implements LlmClient {

    private final String message;

    public UnavailableLlmClient(String message) {
        this.message = message;
    }

    @Override
    public <T> LlmResult<T> structured(LlmRequest request, Class<T> type, String schemaId) {
        throw new IntegrationException(message);
    }
}
