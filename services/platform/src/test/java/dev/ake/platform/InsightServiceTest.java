package dev.ake.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ake.platform.domain.Models.UserStory;
import dev.ake.platform.service.AuditLog;
import dev.ake.platform.service.InsightService;
import dev.ake.platform.service.StoryService;
import dev.ake.platform.store.StoryStore;
import dev.ake.platform.store.memory.InMemoryStoryStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsightServiceTest {

    @Test
    void flagsLowConfidenceStoriesOnTheWatchlist() {
        StoryStore store = new InMemoryStoryStore();
        store.save(story("a", 3, 0.8, List.of()));
        store.save(story("b", 13, 0.3, List.of("security", "migration")));
        StoryService stories = new StoryService(store, null, new AuditLog());
        var insights = new InsightService(stories).summarize();
        assertEquals(2, insights.stories());
        assertEquals(1, insights.watchlist().size());
        assertEquals("b", insights.watchlist().getFirst().id());
        assertTrue(insights.notes().getFirst().contains("points"));
    }

    private static UserStory story(String id, int points, double confidence, List<String> risks) {
        return new UserStory(
                id,
                id,
                "dispatcher",
                "act",
                "benefit",
                "As a dispatcher, I want to act, so that benefit.",
                List.of("one", "two", "three"),
                "medium",
                "draft",
                points,
                confidence,
                risks,
                List.of("because"),
                List.of(),
                List.of(),
                "local",
                Instant.parse("2026-10-31T12:00:00Z"));
    }
}
