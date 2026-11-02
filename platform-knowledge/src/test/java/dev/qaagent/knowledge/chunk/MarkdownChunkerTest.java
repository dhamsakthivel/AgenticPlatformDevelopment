package dev.qaagent.knowledge.chunk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownChunkerTest {

    private final MarkdownChunker chunker = new MarkdownChunker();

    @Test
    void blankTextProducesNoChunks() {
        assertThat(chunker.chunk("  \n")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void shortNoteStaysOneChunk() {
        assertThat(chunker.chunk("# Spouse\n\nA spouse may enroll.")).containsExactly("# Spouse\n\nA spouse may enroll.");
    }

    @Test
    void headingsStayApartOnceEachSectionReachesTheMinimum() {
        String spouse = "# Spouse\n\n" + "spouse ".repeat(400);
        String child = "# Child\n\n" + "child ".repeat(400);

        var chunks = chunker.chunk(spouse + "\n" + child);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).contains("# Spouse").doesNotContain("# Child");
        assertThat(chunks.get(1)).contains("# Child");
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(MarkdownChunker.tokens(chunk)).isBetween(MarkdownChunker.MIN_TOKENS, MarkdownChunker.MAX_TOKENS));
    }

    @Test
    void oversizedParagraphIsSplitAndNoChunkExceedsTheMaximum() {
        String wall = "word ".repeat(2000);

        var chunks = chunker.chunk("# Rules\n\n" + wall);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(MarkdownChunker.tokens(chunk)).isLessThanOrEqualTo(MarkdownChunker.MAX_TOKENS));
    }
}
