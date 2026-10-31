package dev.ake.platform.store.memory;

import dev.ake.platform.domain.Models.KnowledgeDocument;
import dev.ake.platform.store.DocumentStore;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class InMemoryDocumentStore implements DocumentStore {

    private final ConcurrentHashMap<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        documents.put(document.id(), document);
        return document;
    }

    @Override
    public List<KnowledgeDocument> findAll() {
        return documents.values().stream()
                .sorted(Comparator.comparing(KnowledgeDocument::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<KnowledgeDocument> findById(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    @Override
    public void delete(String id) {
        documents.remove(id);
    }
}
