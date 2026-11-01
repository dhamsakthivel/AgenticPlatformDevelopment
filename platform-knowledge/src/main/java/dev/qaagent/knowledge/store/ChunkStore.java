package dev.qaagent.knowledge.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.knowledge.retrieve.RankedChunk;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
public class ChunkStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ChunkStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<StoredDocument> find(String source, String uri) {
        List<StoredDocument> found = jdbc.query("""
                SELECT id, content_hash FROM documents
                WHERE source = ? AND uri IS NOT DISTINCT FROM ?
                ORDER BY ingested_at DESC NULLS LAST
                LIMIT 1
                """, (rs, row) -> new StoredDocument(rs.getObject("id", UUID.class), rs.getString("content_hash")),
                source, uri);
        return found.stream().findFirst();
    }

    public void replace(StoredDocument existing, String source, String title, String app, String module, String version,
                        String contentHash, List<String> chunks, List<float[]> embeddings, String embeddingModel) {
        jdbc.update("DELETE FROM chunks WHERE document_id = ?", existing.id());
        jdbc.update("""
                UPDATE documents
                SET title = ?, app = ?, module = ?, version = ?, content_hash = ?, phi_scrubbed = true, ingested_at = ?
                WHERE id = ?
                """, title, app, module, version, contentHash, Timestamp.from(Instant.now()), existing.id());
        insertChunks(existing.id(), title, app, module, source, chunks, embeddings, embeddingModel);
    }

    public UUID insert(String source, String uri, String title, String app, String module, String version,
                       String contentHash, List<String> chunks, List<float[]> embeddings, String embeddingModel) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO documents
                    (id, source, uri, title, app, module, version, content_hash, phi_scrubbed, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, true, ?)
                """, id, source, uri, title, app, module, version, contentHash, Timestamp.from(Instant.now()));
        insertChunks(id, title, app, module, source, chunks, embeddings, embeddingModel);
        return id;
    }

    public List<RankedChunk> nearest(float[] query, SearchFilter filter, int limit) {
        SearchFilter.Query querySql = filter.where("c.embedding IS NOT NULL");
        String sql = """
                SELECT c.id, c.document_id, c.text, d.title, d.source, d.app, d.module,
                       1 - (c.embedding <=> ?::vector) AS score
                FROM chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE %s
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
                """.formatted(querySql.clause());
        List<Object> args = new ArrayList<>();
        args.add(vector(query));
        args.addAll(querySql.args());
        args.add(vector(query));
        args.add(limit);
        return jdbc.query(sql, this::mapChunk, args.toArray());
    }

    public List<RankedChunk> fullText(String query, SearchFilter filter, int limit) {
        SearchFilter.Query querySql = filter.where("c.tsv @@ plainto_tsquery('english', ?)");
        String sql = """
                SELECT c.id, c.document_id, c.text, d.title, d.source, d.app, d.module,
                       ts_rank(c.tsv, plainto_tsquery('english', ?)) AS score
                FROM chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE %s
                ORDER BY score DESC
                LIMIT ?
                """.formatted(querySql.clause());
        List<Object> args = new ArrayList<>();
        args.add(query);
        args.add(query);
        args.addAll(querySql.args());
        args.add(limit);
        return jdbc.query(sql, this::mapChunk, args.toArray());
    }

    private void insertChunks(UUID documentId, String title, String app, String module, String source,
                              List<String> chunks, List<float[]> embeddings, String embeddingModel) {
        for (int index = 0; index < chunks.size(); index++) {
            jdbc.update("""
                    INSERT INTO chunks
                        (id, document_id, chunk_index, text, embedding, tsv, embedding_model, metadata)
                    VALUES (?, ?, ?, ?, ?::vector, to_tsvector('english', ?), ?, ?::jsonb)
                    """,
                    UUID.randomUUID(),
                    documentId,
                    index,
                    chunks.get(index),
                    vector(embeddings.get(index)),
                    chunks.get(index),
                    embeddingModel,
                    metadata(title, app, module, source));
        }
    }

    private RankedChunk mapChunk(ResultSet rs, int row) throws SQLException {
        return new RankedChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("title"),
                rs.getString("source"),
                rs.getString("app"),
                rs.getString("module"),
                rs.getString("text"),
                rs.getDouble("score"));
    }

    private PGobject metadata(String title, String app, String module, String source) {
        try {
            PGobject value = new PGobject();
            value.setType("jsonb");
            value.setValue(json.writeValueAsString(java.util.Map.of(
                    "title", title == null ? "" : title,
                    "app", app == null ? "" : app,
                    "module", module == null ? "" : module,
                    "source", source == null ? "" : source)));
            return value;
        } catch (JsonProcessingException | SQLException exception) {
            throw new IntegrationException("Could not store chunk metadata.", exception);
        }
    }

    static String vector(float[] values) {
        StringBuilder text = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                text.append(',');
            }
            text.append(String.format(Locale.US, "%.6f", values[index]));
        }
        return text.append(']').toString();
    }

    public record StoredDocument(UUID id, String contentHash) {
    }
}
