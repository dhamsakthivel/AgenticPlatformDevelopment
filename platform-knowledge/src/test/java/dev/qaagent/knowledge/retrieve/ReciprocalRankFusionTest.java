package dev.qaagent.knowledge.retrieve;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReciprocalRankFusionTest {

    @Test
    void itemPresentInBothListsOutranksAnItemInOnlyOne() {
        RankedChunk spouse = hit("spouse");
        RankedChunk retro = hit("retro");
        RankedChunk child = hit("child");

        List<RankedChunk> fused = ReciprocalRankFusion.fuse(
                List.of(spouse, retro),
                List.of(retro, child),
                3);

        assertThat(fused).extracting(RankedChunk::text).containsExactly("retro", "spouse", "child");
        assertThat(fused.getFirst().score()).isCloseTo(1d / 61 + 1d / 62, within(0.000001));
        assertThat(fused.get(1).score()).isCloseTo(1d / 61, within(0.000001));
    }

    @Test
    void limitKeepsTheHighestScores() {
        List<RankedChunk> fused = ReciprocalRankFusion.fuse(
                List.of(hit("a"), hit("b"), hit("c")),
                List.of(),
                1);

        assertThat(fused).extracting(RankedChunk::text).containsExactly("a");
    }

    private static RankedChunk hit(String text) {
        return new RankedChunk(UUID.randomUUID(), UUID.randomUUID(), text, "upload",
                "example-benefit-plan", "enrollment", text, 0);
    }
}
