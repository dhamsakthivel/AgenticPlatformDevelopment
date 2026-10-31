package dev.ake.platform.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ake.platform.config.AkeProperties;
import dev.ake.platform.domain.Models.Citation;
import dev.ake.platform.domain.Models.Prediction;
import dev.ake.platform.domain.Models.SystemReport.AgentStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ake.agent.mode", havingValue = "http", matchIfMissing = true)
public class HttpAgentClient implements AgentClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper;
    private final AkeProperties properties;

    public HttpAgentClient(ObjectMapper mapper, AkeProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public IngestResult ingest(String id, String title, String text, String source, List<String> tags) {
        WireIngest wire = send(
                "POST",
                "/v1/documents/ingest",
                Map.of("id", id, "title", title, "text", text, "source", source == null ? "upload" : source, "tags", orEmpty(tags)),
                WireIngest.class);
        return new IngestResult(wire.id, wire.chunks, wire.backend);
    }

    @Override
    public void delete(String documentId) {
        send("DELETE", "/v1/documents/" + documentId, null, Map.class);
    }

    @Override
    public QueryResult query(String query, int topK, List<String> documentIds) {
        return send(
                        "POST",
                        "/v1/rag/query",
                        Map.of("query", query, "top_k", topK, "document_ids", orEmpty(documentIds)),
                        WireQuery.class)
                .toResult();
    }

    @Override
    public Draft generate(String prompt, List<String> documentIds) {
        WireDraft wire = send(
                "POST",
                "/v1/stories/generate",
                Map.of("prompt", prompt, "document_ids", orEmpty(documentIds)),
                WireDraft.class);
        return wire.toDraft();
    }

    @Override
    public Prediction estimate(String title, String description, List<String> acceptanceCriteria) {
        WirePrediction wire = send(
                "POST",
                "/v1/stories/estimate",
                Map.of(
                        "title",
                        title,
                        "description",
                        description == null ? "" : description,
                        "acceptance_criteria",
                        orEmpty(acceptanceCriteria)),
                WirePrediction.class);
        return wire.toPrediction();
    }

    @Override
    public AgentStatus status() {
        try {
            WireStatus wire = send("GET", "/v1/system", null, WireStatus.class);
            return new AgentStatus(
                    wire.status,
                    wire.llm,
                    wire.embeddings,
                    wire.vector,
                    wire.chunks,
                    wire.langsmith,
                    wire.langsmithProject == null ? "" : wire.langsmithProject,
                    wire.mcp == null ? List.of() : wire.mcp);
        } catch (AgentException exception) {
            return new AgentStatus("down", "", "", "", 0, false, exception.getMessage(), List.of());
        }
    }

    private <T> T send(String method, String path, Object body, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(trim(properties.agent().baseUrl()) + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("X-API-Key", properties.agent().apiKey())
                    .header("Accept", "application/json");
            if ("GET".equals(method)) {
                builder.GET();
            } else if ("DELETE".equals(method)) {
                builder.DELETE();
            } else {
                builder.header("Content-Type", "application/json");
                builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new AgentException("Agent " + path + " returned " + response.statusCode());
            }
            if (type == Map.class) {
                return type.cast(Map.of("ok", true));
            }
            return mapper.readValue(response.body(), type);
        } catch (AgentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AgentException("Agent runtime is unreachable at " + properties.agent().baseUrl());
        }
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String trim(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class WireIngest {
        public String id;
        public int chunks;
        public String backend;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class WireQuery {
        public List<WireCitation> citations;
        public String backend;

        QueryResult toResult() {
            List<Citation> cites = citations == null
                    ? List.of()
                    : citations.stream().map(WireCitation::toCitation).toList();
            return new QueryResult(cites, backend);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class WireCitation {
        @JsonProperty("document_id")
        public String documentId;
        public String title;
        public String section;
        public String excerpt;
        public double score;

        Citation toCitation() {
            return new Citation(documentId, title, section, excerpt, score);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class WireDraft {
        public String title;
        public String persona;
        public String action;
        public String benefit;
        public String description;
        @JsonProperty("acceptance_criteria")
        public List<String> acceptanceCriteria;
        public String priority;
        public List<WireCitation> citations;
        public List<String> notes;
        public WirePrediction prediction;
        public String model;

        Draft toDraft() {
            List<Citation> cites = citations == null
                    ? List.of()
                    : citations.stream().map(WireCitation::toCitation).toList();
            return new Draft(
                    title,
                    persona,
                    action,
                    benefit,
                    description,
                    acceptanceCriteria == null ? List.of() : acceptanceCriteria,
                    priority,
                    cites,
                    notes == null ? List.of() : notes,
                    prediction == null ? new Prediction(0, 0, List.of(), List.of(), 0) : prediction.toPrediction(),
                    model);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class WirePrediction {
        public int points;
        public double confidence;
        public List<String> risks;
        public List<String> rationale;
        public int neighbors;

        Prediction toPrediction() {
            return new Prediction(
                    points,
                    confidence,
                    risks == null ? List.of() : risks,
                    rationale == null ? List.of() : rationale,
                    neighbors);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class WireStatus {
        public String status;
        public String llm;
        public String embeddings;
        public String vector;
        public int chunks;
        public boolean langsmith;
        @JsonProperty("langsmithProject")
        public String langsmithProject;
        public List<String> mcp;
    }
}
