package dev.qaagent.knowledge.retrieve;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReciprocalRankFusion {

    public static final int K = 60;

    private ReciprocalRankFusion() {
    }

    public static List<RankedChunk> fuse(List<RankedChunk> vectorHits, List<RankedChunk> textHits, int limit) {
        Map<UUID, RankedChunk> chunks = new LinkedHashMap<>();
        Map<UUID, Double> scores = new LinkedHashMap<>();
        add(vectorHits, chunks, scores);
        add(textHits, chunks, scores);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(Math.max(limit, 0))
                .map(entry -> withScore(chunks.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    private static void add(List<RankedChunk> hits, Map<UUID, RankedChunk> chunks, Map<UUID, Double> scores) {
        int rank = 1;
        for (RankedChunk hit : hits) {
            chunks.putIfAbsent(hit.chunkId(), hit);
            scores.merge(hit.chunkId(), 1d / (K + rank), Double::sum);
            rank++;
        }
    }

    private static RankedChunk withScore(RankedChunk chunk, double score) {
        return new RankedChunk(chunk.chunkId(), chunk.documentId(), chunk.title(), chunk.source(),
                chunk.app(), chunk.module(), chunk.text(), score);
    }
}
