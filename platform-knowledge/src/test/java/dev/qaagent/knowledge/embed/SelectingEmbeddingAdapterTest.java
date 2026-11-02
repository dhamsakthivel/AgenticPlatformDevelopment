package dev.qaagent.knowledge.embed;

import dev.qaagent.core.port.EmbeddingPort;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SelectingEmbeddingAdapterTest {

    private final Map<String, String> values = new HashMap<>();
    private final AtomicInteger built = new AtomicInteger();
    private final SelectingEmbeddingAdapter adapter = new SelectingEmbeddingAdapter(
            new FixedPort("local-token-hash", new float[] {1f}),
            values::get,
            (apiKey, model) -> {
                built.incrementAndGet();
                return new FixedPort(model, new float[] {2f});
            });

    @Test
    void anEmptyKeyKeepsTheLocalStandIn() {
        assertThat(adapter.modelName()).isEqualTo("local-token-hash");
        assertThat(adapter.embed(List.of("spouse")).getFirst()).containsExactly(1f);
        assertThat(built).hasValue(0);
    }

    @Test
    void aKeyReadOnTheCallSwitchesTheModelAndALaterEmptyKeySwitchesBack() {
        values.put("OPENAI_API_KEY", "sk-test");
        values.put("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small");

        assertThat(adapter.modelName()).isEqualTo("text-embedding-3-small");
        assertThat(adapter.embed(List.of("spouse")).getFirst()[0]).isEqualTo(2f);
        assertThat(adapter.embed(List.of("spouse")).getFirst()[0]).isEqualTo(2f);

        values.put("OPENAI_API_KEY", " ");
        assertThat(adapter.modelName()).isEqualTo("local-token-hash");
        assertThat(built).hasValue(1);
    }

    private record FixedPort(String model, float[] vector) implements EmbeddingPort {
        @Override
        public List<float[]> embed(List<String> texts) {
            return List.of(vector);
        }

        @Override
        public String modelName() {
            return model;
        }
    }
}
