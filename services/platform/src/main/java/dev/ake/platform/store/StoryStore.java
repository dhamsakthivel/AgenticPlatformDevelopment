package dev.ake.platform.store;

import dev.ake.platform.domain.Models.UserStory;
import java.util.List;
import java.util.Optional;

public interface StoryStore {

    UserStory save(UserStory story);

    List<UserStory> findAll();

    Optional<UserStory> findById(String id);

    void delete(String id);
}
