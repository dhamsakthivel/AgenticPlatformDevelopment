package dev.ake.platform.store.mongo;

import dev.ake.platform.domain.Models.KnowledgeDocument;
import dev.ake.platform.store.DocumentStore;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@Profile("cloud")
public class MongoDocumentStore implements DocumentStore {

    private final MongoTemplate mongo;

    public MongoDocumentStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        mongo.save(toEntity(document));
        return document;
    }

    @Override
    public List<KnowledgeDocument> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongo.find(query, StoredDocument.class).stream().map(MongoDocumentStore::toModel).toList();
    }

    @Override
    public Optional<KnowledgeDocument> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, StoredDocument.class)).map(MongoDocumentStore::toModel);
    }

    @Override
    public void delete(String id) {
        mongo.remove(Query.query(Criteria.where("_id").is(id)), StoredDocument.class);
    }

    private static StoredDocument toEntity(KnowledgeDocument document) {
        StoredDocument entity = new StoredDocument();
        entity.setId(document.id());
        entity.setTitle(document.title());
        entity.setSource(document.source());
        entity.setText(document.text());
        entity.setTags(document.tags());
        entity.setIngestStatus(document.ingestStatus());
        entity.setChunks(document.chunks());
        entity.setCreatedAt(document.createdAt());
        return entity;
    }

    private static KnowledgeDocument toModel(StoredDocument entity) {
        return new KnowledgeDocument(
                entity.getId(),
                entity.getTitle(),
                entity.getSource(),
                entity.getText(),
                entity.getTags() == null ? List.of() : List.copyOf(entity.getTags()),
                entity.getIngestStatus(),
                entity.getChunks(),
                entity.getCreatedAt());
    }
}
