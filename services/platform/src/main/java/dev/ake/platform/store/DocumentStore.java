package dev.ake.platform.store;

import dev.ake.platform.domain.Models.KnowledgeDocument;
import java.util.List;
import java.util.Optional;

public interface DocumentStore {

    KnowledgeDocument save(KnowledgeDocument document);

    List<KnowledgeDocument> findAll();

    Optional<KnowledgeDocument> findById(String id);

    void delete(String id);
}
