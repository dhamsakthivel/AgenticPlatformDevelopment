package dev.qaagent.knowledge.embed;

import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.EmbeddingPort;
import dev.qaagent.knowledge.config.KnowledgeProperties;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Embeds redacted text with the configured OpenAI model, in batches, and logs each call.
 */
public final class OpenAiEmbeddingAdapter implements EmbeddingPort {

    private final OpenAiEmbeddingModel model;
    private final String modelName;
    private final PhiRedactor redactor;
    private final LlmCallLogger calls;
    private final KnowledgeProperties knowledge;

    public OpenAiEmbeddingAdapter(String apiKey, String modelName, int dimensions,
                                  PhiRedactor redactor, LlmCallLogger calls, KnowledgeProperties knowledge) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IntegrationException("OPENAI_API_KEY is empty.");
        }
        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(modelName)
                .dimensions(dimensions)
                .build();
        this.model = new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
        this.modelName = modelName;
        this.redactor = redactor;
        this.calls = calls;
        this.knowledge = knowledge;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<String> redacted = texts.stream().map(redactor::redact).toList();
        int batchSize = Math.max(1, knowledge.embeddingBatchSize());
        List<float[]> vectors = new ArrayList<>(redacted.size());
        for (int start = 0; start < redacted.size(); start += batchSize) {
            List<String> batch = redacted.subList(start, Math.min(redacted.size(), start + batchSize));
            vectors.addAll(embedBatch(batch));
        }
        return List.copyOf(vectors);
    }

    private List<float[]> embedBatch(List<String> batch) {
        long started = System.nanoTime();
        try {
            EmbeddingResponse response = model.call(new EmbeddingRequest(batch, null));
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            int tokens = tokens(response, batch);
            calls.record(modelName, tokens, latencyMs,
                    LlmCallLogger.cost(tokens, knowledge.embeddingUsdPerMillionTokens()), true);
            List<float[]> vectors = new ArrayList<>(response.getResults().size());
            for (Embedding embedding : response.getResults()) {
                vectors.add(embedding.getOutput());
            }
            if (vectors.size() != batch.size()) {
                throw new IntegrationException("OpenAI returned " + vectors.size()
                        + " embeddings for " + batch.size() + " texts.");
            }
            return vectors;
        } catch (IntegrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            calls.record(modelName, LlmCallLogger.estimateTokens(String.join("\n", batch)), latencyMs,
                    java.math.BigDecimal.ZERO, false);
            throw new IntegrationException("OpenAI embedding request failed.", exception);
        }
    }

    private static int tokens(EmbeddingResponse response, List<String> batch) {
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null
                && response.getMetadata().getUsage().getPromptTokens() != null) {
            return response.getMetadata().getUsage().getPromptTokens();
        }
        return batch.stream().mapToInt(LlmCallLogger::estimateTokens).sum();
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
