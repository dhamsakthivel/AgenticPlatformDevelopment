package dev.ake.platform.store.memory;

import dev.ake.platform.domain.Models.UserStory;
import dev.ake.platform.store.StoryStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class InMemoryStoryStore implements StoryStore {

    private final ConcurrentHashMap<String, UserStory> stories = new ConcurrentHashMap<>();

    @Override
    public UserStory save(UserStory story) {
        stories.put(story.id(), story);
        return story;
    }

    @Override
    public List<UserStory> findAll() {
        return stories.values().stream()
                .sorted(Comparator.comparing(UserStory::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<UserStory> findById(String id) {
        return Optional.ofNullable(stories.get(id));
    }

    @Override
    public void delete(String id) {
        stories.remove(id);
    }
}
