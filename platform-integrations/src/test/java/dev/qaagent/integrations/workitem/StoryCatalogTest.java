package dev.qaagent.integrations.workitem;

import dev.qaagent.core.IntegrationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryCatalogTest {

    @Test
    void seedFileHasEighteenEnrollmentStoriesIncludingThreeVagueOnes() {
        List<SeedStory> stories = StoryCatalog.load(StoryCatalog.resolve("scripts/seed/stories.json"));

        assertThat(stories).hasSize(18);
        assertThat(stories.stream().filter(SeedStory::vague)).hasSize(3);
        assertThat(stories).allSatisfy(story -> {
            assertThat(story.module()).isEqualTo("enrollment");
            assertThat(story.id()).startsWith("ENR-");
        });
        assertThat(stories).filteredOn(story -> "ENR-017".equals(story.id()))
                .singleElement()
                .satisfies(story -> assertThat(story.acceptanceCriteria()).isEmpty());
    }

    @Test
    void aMissingSeedFileIsReported() {
        assertThatThrownBy(() -> StoryCatalog.resolve("scripts/seed/does-not-exist.json"))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("Stories file not found");
    }
}
