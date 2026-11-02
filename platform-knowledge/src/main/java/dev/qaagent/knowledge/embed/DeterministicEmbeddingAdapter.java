package dev.qaagent.knowledge.embed;

import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.EmbeddingPort;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline stand-in used when OPENAI_API_KEY is empty.
 * Tokens are hashed into a unit vector so shared words land near each other.
 */
public final class DeterministicEmbeddingAdapter implements EmbeddingPort {

    public static final String MODEL = "local-token-hash";

    private final PhiRedactor redactor;
    private final LlmCallLogger calls;
    private final int dimensions;

    public DeterministicEmbeddingAdapter(PhiRedactor redactor, LlmCallLogger calls, int dimensions) {
        this.redactor = redactor;
        this.calls = calls;
        this.dimensions = dimensions;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        long started = System.nanoTime();
        List<float[]> vectors = new ArrayList<>(texts.size());
        int tokens = 0;
        for (String text : texts) {
            String redacted = redactor.redact(text);
            tokens += LlmCallLogger.estimateTokens(redacted);
            vectors.add(vector(redacted));
        }
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        calls.record(MODEL, tokens, latencyMs, java.math.BigDecimal.ZERO, true);
        return List.copyOf(vectors);
    }

    @Override
    public String modelName() {
        return MODEL;
    }

    private float[] vector(String text) {
        float[] values = new float[dimensions];
        for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
            if (token.isBlank()) {
                continue;
            }
            values[Math.floorMod(token.hashCode(), dimensions)] += 1f;
        }
        double sum = 0;
        for (float value : values) {
            sum += (double) value * value;
        }
        if (sum == 0) {
            values[0] = 1f;
            return values;
        }
        float norm = (float) Math.sqrt(sum);
        for (int index = 0; index < values.length; index++) {
            values[index] /= norm;
        }
        return values;
    }
}
