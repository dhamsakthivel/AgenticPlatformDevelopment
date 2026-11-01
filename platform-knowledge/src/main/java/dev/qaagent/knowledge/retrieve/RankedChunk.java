package dev.qaagent.knowledge.retrieve;

import java.util.UUID;

public record RankedChunk(
        UUID chunkId,
        UUID documentId,
        String title,
        String source,
        String app,
        String module,
        String text,
        double score
) {
}
