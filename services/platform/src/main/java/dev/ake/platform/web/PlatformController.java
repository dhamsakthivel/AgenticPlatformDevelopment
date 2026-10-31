package dev.ake.platform.web;

import dev.ake.platform.agent.AgentClient;
import dev.ake.platform.agent.AgentException;
import dev.ake.platform.ai.StoryCoach;
import dev.ake.platform.config.AkeProperties;
import dev.ake.platform.domain.Models.CoachNote;
import dev.ake.platform.domain.Models.Insights;
import dev.ake.platform.domain.Models.KnowledgeDocument;
import dev.ake.platform.domain.Models.Prediction;
import dev.ake.platform.domain.Models.SystemReport;
import dev.ake.platform.domain.Models.UserStory;
import dev.ake.platform.service.AuditLog;
import dev.ake.platform.service.DocumentService;
import dev.ake.platform.service.InsightService;
import dev.ake.platform.service.StoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlatformController {

    private final DocumentService documents;
    private final StoryService stories;
    private final InsightService insights;
    private final AgentClient agent;
    private final StoryCoach coach;
    private final AuditLog audit;
    private final AkeProperties properties;
    private final Environment environment;

    public PlatformController(
            DocumentService documents,
            StoryService stories,
            InsightService insights,
            AgentClient agent,
            StoryCoach coach,
            AuditLog audit,
            AkeProperties properties,
            Environment environment) {
        this.documents = documents;
        this.stories = stories;
        this.insights = insights;
        this.agent = agent;
        this.coach = coach;
        this.audit = audit;
        this.properties = properties;
        this.environment = environment;
    }

    @GetMapping("/documents")
    public List<KnowledgeDocument.Summary> listDocuments() {
        return documents.list().stream().map(KnowledgeDocument::summary).toList();
    }

    @GetMapping("/documents/{id}")
    public KnowledgeDocument document(@PathVariable String id) {
        return documents.require(id);
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeDocument createDocument(@Valid @RequestBody CreateDocument request) {
        return documents.create(request.title(), request.text(), request.source(), request.tags());
    }

    @PostMapping("/documents/{id}/reindex")
    public KnowledgeDocument reindex(@PathVariable String id) {
        return documents.reindex(id);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable String id) {
        documents.delete(id);
    }

    @PostMapping("/rag/query")
    public AgentClient.QueryResult query(@Valid @RequestBody QueryRequest request) {
        try {
            return agent.query(request.query(), request.topK() == null ? 4 : request.topK(), request.documentIds());
        } catch (AgentException exception) {
            throw exception;
        }
    }

    @PostMapping("/stories/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public UserStory generate(@Valid @RequestBody GenerateStory request) {
        return stories.generate(request.prompt(), request.documentIds());
    }

    @GetMapping("/stories")
    public List<UserStory> listStories() {
        return stories.list();
    }

    @GetMapping("/stories/{id}")
    public UserStory story(@PathVariable String id) {
        return stories.require(id);
    }

    @PatchMapping("/stories/{id}")
    public UserStory updateStory(@PathVariable String id, @RequestBody StoryPatch patch) {
        return stories.update(id, patch.title(), patch.status(), patch.priority(), patch.points());
    }

    @PostMapping("/stories/estimate")
    public Prediction estimate(@Valid @RequestBody EstimateRequest request) {
        return stories.estimate(request.title(), request.description(), request.acceptanceCriteria());
    }

    @PostMapping("/stories/{id}/coach")
    public CoachNote coach(@PathVariable String id) {
        CoachNote note = coach.sharpen(stories.require(id));
        audit.record("story.coach", id, note.enabled() ? "spring-ai" : "idle");
        return note;
    }

    @GetMapping("/insights")
    public Insights insightReport() {
        return insights.summarize();
    }

    @GetMapping("/system")
    public SystemReport system() {
        String storage = environment.matchesProfiles("cloud") ? "mongodb+postgres" : "memory";
        return new SystemReport(
                "up",
                storage,
                agent.status(),
                new SystemReport.Security(
                        true,
                        "X-API-Key",
                        "no — the Next.js server holds the key",
                        List.of(
                                "Stateless API key on every platform call",
                                "Constant-time key compare",
                                "Request bodies capped and validated",
                                "Nosniff, no-store, and frame denial headers",
                                "Story changes written to the audit log")),
                audit.latest());
    }

    public record CreateDocument(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 200_000) String text,
            @Size(max = 120) String source,
            List<@Size(max = 40) String> tags) {}

    public record QueryRequest(
            @NotBlank @Size(max = 8_000) String query, Integer topK, List<String> documentIds) {}

    public record GenerateStory(@NotBlank @Size(max = 8_000) String prompt, List<String> documentIds) {}

    public record EstimateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 8_000) String description,
            List<@Size(max = 2_000) String> acceptanceCriteria) {}

    public record StoryPatch(String title, String status, String priority, Integer points) {}
}
