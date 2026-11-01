package dev.qaagent.knowledge.retrieve;

import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.EmbeddingPort;
import dev.qaagent.knowledge.store.ChunkStore;
import dev.qaagent.knowledge.store.SearchFilter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HybridRetriever {

    private static final int CANDIDATES = 20;

    private final EmbeddingPort embeddings;
    private final ChunkStore store;
    private final PhiRedactor redactor;

    public HybridRetriever(EmbeddingPort embeddings, ChunkStore store, PhiRedactor redactor) {
        this.embeddings = embeddings;
        this.store = store;
        this.redactor = redactor;
    }

    public List<RankedChunk> search(String query, SearchFilter filter, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String redacted = redactor.redact(query);
        float[] vector = embeddings.embed(List.of(redacted)).getFirst();
        List<RankedChunk> byVector = store.nearest(vector, filter, CANDIDATES);
        List<RankedChunk> byText = store.fullText(redacted, filter, CANDIDATES);
        return ReciprocalRankFusion.fuse(byVector, byText, limit);
    }
}
