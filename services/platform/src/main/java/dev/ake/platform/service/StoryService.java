package dev.ake.platform.service;

import dev.ake.platform.agent.AgentClient;
import dev.ake.platform.domain.Models.Prediction;
import dev.ake.platform.domain.Models.UserStory;
import dev.ake.platform.store.StoryStore;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StoryService {

    private static final Set<String> STATUSES = Set.of("draft", "ready", "committed");
    private static final Set<String> PRIORITIES = Set.of("low", "medium", "high", "urgent");
    private static final Set<Integer> POINTS = Set.of(1, 2, 3, 5, 8, 13, 21);

    private final StoryStore stories;
    private final AgentClient agent;
    private final AuditLog audit;

    public StoryService(StoryStore stories, AgentClient agent, AuditLog audit) {
        this.stories = stories;
        this.agent = agent;
        this.audit = audit;
    }

    public UserStory generate(String prompt, List<String> documentIds) {
        AgentClient.Draft draft = agent.generate(prompt, documentIds == null ? List.of() : documentIds);
        Prediction prediction = draft.prediction();
        UserStory story = new UserStory(
                UUID.randomUUID().toString(),
                draft.title(),
                draft.persona(),
                draft.action(),
                draft.benefit(),
                draft.description(),
                draft.acceptanceCriteria(),
                draft.priority(),
                "draft",
                prediction.points(),
                prediction.confidence(),
                prediction.risks(),
                prediction.rationale(),
                draft.citations(),
                draft.notes(),
                draft.model(),
                Instant.now());
        stories.save(story);
        audit.record("story.generate", story.id(), story.title());
        return story;
    }

    public List<UserStory> list() {
        return stories.findAll();
    }

    public UserStory require(String id) {
        return stories.findById(id).orElseThrow(() -> new NotFoundException("Story " + id + " was not found."));
    }

    public UserStory update(String id, String title, String status, String priority, Integer points) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status.trim().toLowerCase())) {
            throw new BadRequestException("Status must be draft, ready, or committed.");
        }
        if (priority != null && !priority.isBlank() && !PRIORITIES.contains(priority.trim().toLowerCase())) {
            throw new BadRequestException("Priority must be low, medium, high, or urgent.");
        }
        if (points != null && !POINTS.contains(points)) {
            throw new BadRequestException("Points must be a Fibonacci value from 1 to 21.");
        }
        UserStory updated = require(id).update(title, status, priority, points);
        stories.save(updated);
        audit.record("story.update", id, updated.status() + " · " + updated.points() + " pts");
        return updated;
    }

    public Prediction estimate(String title, String description, List<String> acceptanceCriteria) {
        Prediction prediction = agent.estimate(
                title, description == null ? "" : description, acceptanceCriteria == null ? List.of() : acceptanceCriteria);
        audit.record("story.estimate", title, prediction.points() + " pts");
        return prediction;
    }
}
