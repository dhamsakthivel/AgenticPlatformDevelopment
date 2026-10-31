package dev.ake.platform.service;

import dev.ake.platform.agent.AgentClient;
import dev.ake.platform.agent.AgentException;
import dev.ake.platform.domain.Models.KnowledgeDocument;
import dev.ake.platform.store.DocumentStore;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final DocumentStore documents;
    private final AgentClient agent;
    private final AuditLog audit;

    public DocumentService(DocumentStore documents, AgentClient agent, AuditLog audit) {
        this.documents = documents;
        this.agent = agent;
        this.audit = audit;
    }

    public KnowledgeDocument create(String title, String text, String source, List<String> tags) {
        String id = UUID.randomUUID().toString();
        KnowledgeDocument created = new KnowledgeDocument(
                id,
                title.trim(),
                source == null || source.isBlank() ? "upload" : source.trim(),
                text,
                cleanTags(tags),
                "pending",
                0,
                Instant.now());
        documents.save(created);
        return reindex(id);
    }

    public KnowledgeDocument seed(String id, String title, String text) {
        if (documents.findById(id).isPresent()) {
            return documents.findById(id).orElseThrow();
        }
        documents.save(new KnowledgeDocument(id, title, "seed", text, List.of("prd"), "pending", 0, Instant.now()));
        return reindex(id);
    }

    public KnowledgeDocument reindex(String id) {
        KnowledgeDocument document = require(id);
        try {
            AgentClient.IngestResult result =
                    agent.ingest(document.id(), document.title(), document.text(), document.source(), document.tags());
            KnowledgeDocument indexed = document.withIngest("indexed", result.chunks());
            documents.save(indexed);
            audit.record("document.index", id, indexed.chunks() + " chunks");
            return indexed;
        } catch (AgentException exception) {
            KnowledgeDocument failed = document.withIngest("failed", document.chunks());
            documents.save(failed);
            audit.record("document.index_failed", id, exception.getMessage());
            return failed;
        }
    }

    public List<KnowledgeDocument> list() {
        return documents.findAll();
    }

    public KnowledgeDocument require(String id) {
        return documents.findById(id).orElseThrow(() -> new NotFoundException("Document " + id + " was not found."));
    }

    public void delete(String id) {
        require(id);
        try {
            agent.delete(id);
        } catch (AgentException exception) {
            audit.record("document.delete_vector_failed", id, exception.getMessage());
        }
        documents.delete(id);
        audit.record("document.delete", id, "removed from the document store");
    }

    private static List<String> cleanTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .limit(8)
                .toList();
    }
}
