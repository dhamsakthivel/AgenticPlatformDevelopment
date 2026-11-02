package dev.qaagent.integrations.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.config.DotEnv;
import dev.qaagent.core.llm.LlmRequest;
import dev.qaagent.core.llm.LlmResult;
import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.LlmClient;
import dev.qaagent.integrations.config.LlmClientProperties;
import dev.qaagent.integrations.config.OpenAiChatSettings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolvingLlmClientTest {

    private final Map<String, String> values = new HashMap<>();
    private final AtomicInteger built = new AtomicInteger();
    private final ResolvingLlmClient client = new ResolvingLlmClient(
            values::get,
            (apiKey, model) -> {
                built.incrementAndGet();
                return new NamedClient(model);
            });

    @Test
    void theCallReadsTheProcessTheFileAndTheStartupValue() {
        String process = System.getenv("OPENAI_API_KEY");
        String file = DotEnv.fileValues().get("OPENAI_API_KEY");
        Assumptions.assumeTrue(process == null || process.isBlank());
        Assumptions.assumeTrue(file == null || file.isBlank());

        ResolvingLlmClient live = new ResolvingLlmClient(
                new OpenAiChatSettings("  ", "gpt-5-mini"),
                new LlmClientProperties("classpath:llm-prices.yml", 0.2),
                new PhiRedactor(),
                null,
                new ObjectMapper());

        assertThatThrownBy(() -> live.structured(request(), String.class, "schema"))
                .isInstanceOf(IntegrationException.class)
                .hasMessage(ResolvingLlmClient.MISSING_KEY);
    }

    @Test
    void aMissingKeyFailsWithTheSameMessageAndDoesNotCallTheModel() {
        assertThatThrownBy(() -> client.structured(request(), Map.class, "schema"))
                .isInstanceOf(IntegrationException.class)
                .hasMessage(ResolvingLlmClient.MISSING_KEY);
        assertThat(built).hasValue(0);
    }

    @Test
    void aMissingModelFailsAfterTheKeyIsPresent() {
        values.put("OPENAI_API_KEY", "sk-test");

        assertThatThrownBy(() -> client.structured(request(), Map.class, "schema"))
                .isInstanceOf(IntegrationException.class)
                .hasMessage(ResolvingLlmClient.MISSING_MODEL);
        assertThat(built).hasValue(0);
    }

    @Test
    void theKeyAndModelAreReadAgainOnTheNextCall() {
        values.put("OPENAI_API_KEY", "sk-test");
        values.put("OPENAI_CHAT_MODEL", "gpt-5-mini");

        LlmResult<String> first = client.structured(request(), String.class, "schema");
        LlmResult<String> second = client.structured(request(), String.class, "schema");

        values.put("OPENAI_CHAT_MODEL", "gpt-5.1");
        LlmResult<String> third = client.structured(request(), String.class, "schema");

        assertThat(first.model()).isEqualTo("gpt-5-mini");
        assertThat(second.model()).isEqualTo("gpt-5-mini");
        assertThat(third.model()).isEqualTo("gpt-5.1");
        assertThat(built).hasValue(2);
    }

    private static LlmRequest request() {
        return new LlmRequest("story-analysis", "story-analysis", "v1", "story", "{}");
    }

    private record NamedClient(String model) implements LlmClient {
        @Override
        public <T> LlmResult<T> structured(LlmRequest request, Class<T> type, String schemaId) {
            return new LlmResult<>(null, model, 1, 1, 1, BigDecimal.ZERO);
        }
    }
}
