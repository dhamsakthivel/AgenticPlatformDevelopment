package dev.ake.platform.store.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ake.platform.domain.Models.Citation;
import dev.ake.platform.domain.Models.UserStory;
import dev.ake.platform.store.StoryStore;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("cloud")
public class JpaStoryStore implements StoryStore {

    private static final TypeReference<List<Citation>> CITATIONS = new TypeReference<>() {};

    private final StoryJpaRepository repository;
    private final ObjectMapper mapper;

    public JpaStoryStore(StoryJpaRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public UserStory save(UserStory story) {
        repository.save(toEntity(story));
        return story;
    }

    @Override
    public List<UserStory> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toModel).toList();
    }

    @Override
    public Optional<UserStory> findById(String id) {
        return repository.findById(id).map(this::toModel);
    }

    @Override
    public void delete(String id) {
        repository.deleteById(id);
    }

    private StoryEntity toEntity(UserStory story) {
        StoryEntity entity = new StoryEntity();
        entity.setId(story.id());
        entity.setTitle(story.title());
        entity.setPersona(story.persona());
        entity.setAction(story.action());
        entity.setBenefit(story.benefit());
        entity.setDescription(story.description());
        entity.setAcceptanceCriteria(story.acceptanceCriteria());
        entity.setPriority(story.priority());
        entity.setStatus(story.status());
        entity.setPoints(story.points());
        entity.setConfidence(story.confidence());
        entity.setRisks(story.risks());
        entity.setRationale(story.rationale());
        entity.setNotes(story.notes());
        entity.setModel(story.model());
        entity.setCreatedAt(story.createdAt());
        try {
            entity.setCitationsJson(mapper.writeValueAsString(story.citations()));
        } catch (Exception exception) {
            entity.setCitationsJson("[]");
        }
        return entity;
    }

    private UserStory toModel(StoryEntity entity) {
        List<Citation> citations;
        try {
            citations = entity.getCitationsJson() == null
                    ? List.of()
                    : mapper.readValue(entity.getCitationsJson(), CITATIONS);
        } catch (Exception exception) {
            citations = List.of();
        }
        return new UserStory(
                entity.getId(),
                entity.getTitle(),
                entity.getPersona(),
                entity.getAction(),
                entity.getBenefit(),
                entity.getDescription(),
                copy(entity.getAcceptanceCriteria()),
                entity.getPriority(),
                entity.getStatus(),
                entity.getPoints(),
                entity.getConfidence(),
                copy(entity.getRisks()),
                copy(entity.getRationale()),
                citations,
                copy(entity.getNotes()),
                entity.getModel(),
                entity.getCreatedAt());
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
