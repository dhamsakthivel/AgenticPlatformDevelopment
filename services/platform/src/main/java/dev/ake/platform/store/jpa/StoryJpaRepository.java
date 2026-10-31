package dev.ake.platform.store.jpa;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("cloud")
public interface StoryJpaRepository extends JpaRepository<StoryEntity, String> {

    List<StoryEntity> findAllByOrderByCreatedAtDesc();
}
