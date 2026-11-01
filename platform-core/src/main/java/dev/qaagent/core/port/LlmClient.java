package dev.qaagent.core.port;

import dev.qaagent.core.llm.LlmRequest;
import dev.qaagent.core.llm.LlmResult;

public interface LlmClient {

    <T> LlmResult<T> structured(LlmRequest request, Class<T> type, String schemaId);
}
