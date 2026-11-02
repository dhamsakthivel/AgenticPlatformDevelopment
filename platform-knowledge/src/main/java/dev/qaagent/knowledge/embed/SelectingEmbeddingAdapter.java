package dev.qaagent.knowledge.embed;

import dev.qaagent.core.config.DotEnv;
import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.EmbeddingPort;
import dev.qaagent.knowledge.config.KnowledgeProperties;
import dev.qaagent.knowledge.config.OpenAiEmbeddingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

/**
 * Chooses the embedding model on each call. An empty key keeps the local stand-in.
 * A key that appears in the environment or in {@code .env} is used on the next search.
 */
public final class SelectingEmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(SelectingEmbeddingAdapter.class);

    private final EmbeddingPort local;
    private final Function<String, String> values;
    private final RemoteFactory remotes;
    private final Object lock = new Object();

    private String activeKey = "";
    private String activeModel = "";
    private EmbeddingPort remote;

    public SelectingEmbeddingAdapter(OpenAiEmbeddingSettings settings, KnowledgeProperties knowledge,
                                     PhiRedactor redactor, LlmCallLogger calls) {
        this(new DeterministicEmbeddingAdapter(redactor, calls, settings.dimensions()),
                name -> DotEnv.resolve(name, configured(settings, name)),
                (apiKey, model) -> new OpenAiEmbeddingAdapter(apiKey, model, settings.dimensions(),
                        redactor, calls, knowledge));
    }

    SelectingEmbeddingAdapter(EmbeddingPort local, Function<String, String> values, RemoteFactory remotes) {
        this.local = local;
        this.values = values;
        this.remotes = remotes;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return select().embed(texts);
    }

    @Override
    public String modelName() {
        return select().modelName();
    }

    private EmbeddingPort select() {
        String apiKey = blank(values.apply("OPENAI_API_KEY"));
        if (apiKey.isEmpty()) {
            return local;
        }
        String model = blank(values.apply("OPENAI_EMBEDDING_MODEL"));
        if (model.isEmpty()) {
            model = "text-embedding-3-small";
        }
        synchronized (lock) {
            if (remote != null && apiKey.equals(activeKey) && model.equals(activeModel)) {
                return remote;
            }
            log.info("Embeddings will use {}.", model);
            remote = remotes.create(apiKey, model);
            activeKey = apiKey;
            activeModel = model;
            return remote;
        }
    }

    private static String configured(OpenAiEmbeddingSettings settings, String name) {
        if (settings == null) {
            return "";
        }
        String value = switch (name) {
            case "OPENAI_API_KEY" -> settings.apiKey();
            case "OPENAI_EMBEDDING_MODEL" -> settings.model();
            default -> "";
        };
        return value == null ? "" : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    interface RemoteFactory {
        EmbeddingPort create(String apiKey, String model);
    }
}
