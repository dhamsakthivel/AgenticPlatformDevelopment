package dev.qaagent.orchestrator.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.IntegrationException;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RunStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StepEvents events;

    public RunStore(JdbcTemplate jdbc, ObjectMapper json, StepEvents events) {
        this.jdbc = jdbc;
        this.json = json;
        this.events = events;
    }

    public void insertRun(UUID runId, String storyId, String triggeredBy) {
        jdbc.update("""
                INSERT INTO workflow_run
                    (id, story_id, status, triggered_by, temporal_workflow_id, started_at)
                VALUES (?, ?, 'RUNNING', ?, ?, ?)
                """,
                runId, storyId, triggeredBy, runId.toString(), Timestamp.from(Instant.now()));
    }

    public Optional<UUID> activeRun(String storyId) {
        List<UUID> ids = jdbc.query("""
                SELECT id FROM workflow_run
                WHERE story_id = ? AND status IN ('RUNNING', 'WAITING_FOR_APPROVAL')
                ORDER BY started_at DESC
                """,
                (rs, row) -> rs.getObject("id", UUID.class),
                storyId);
        return ids.stream().findFirst();
    }

    public void updateStatus(UUID runId, String status) {
        jdbc.update("UPDATE workflow_run SET status = ? WHERE id = ?", status, runId);
    }

    public void failRun(UUID runId, String error) {
        jdbc.update("""
                UPDATE workflow_run
                SET status = 'FAILED', completed_at = ?, summary = ?
                WHERE id = ? AND status <> 'FAILED'
                """,
                Timestamp.from(Instant.now()), jsonb(java.util.Map.of("error", error == null ? "The run failed." : error)), runId);
    }

    public void completeRun(UUID runId, boolean signaled, String decidedBy, int riskScore) {
        jdbc.update("""
                UPDATE workflow_run
                SET status = 'COMPLETED', completed_at = ?, summary = ?
                WHERE id = ?
                """,
                Timestamp.from(Instant.now()),
                jsonb(java.util.Map.of(
                        "approval", signaled ? "received" : "timedOut",
                        "decidedBy", decidedBy == null ? "" : decidedBy,
                        "riskScore", riskScore)),
                runId);
    }

    public UUID startStep(UUID runId, String stepName, String agent, Object input) {
        UUID stepId = UUID.randomUUID();
        Instant started = Instant.now();
        jdbc.update("""
                INSERT INTO agent_step
                    (id, run_id, step_name, agent, status, input, started_at)
                VALUES (?, ?, ?, ?, 'RUNNING', ?, ?)
                """,
                stepId, runId, stepName, agent, jsonb(input), Timestamp.from(started));
        events.publish(new StepEvent(runId.toString(), stepId.toString(), stepName, "RUNNING", null));
        return stepId;
    }

    public void finishStep(UUID stepId, Object output) {
        jdbc.update("""
                UPDATE agent_step
                SET status = 'SUCCEEDED', output = ?, completed_at = ?
                WHERE id = ?
                """,
                jsonb(output), Timestamp.from(Instant.now()), stepId);
        publish(stepId, "SUCCEEDED", null);
    }

    public void failStep(UUID stepId, String error) {
        jdbc.update("""
                UPDATE agent_step
                SET status = 'FAILED', error = ?, completed_at = ?
                WHERE id = ?
                """,
                error, Timestamp.from(Instant.now()), stepId);
        publish(stepId, "FAILED", error);
    }

    public boolean alreadyPosted(String idempotencyKey) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM audit_event
                WHERE action = 'postStoryComment' AND details->>'idempotencyKey' = ?
                """,
                Integer.class,
                idempotencyKey);
        return count != null && count > 0;
    }

    public void rememberPost(UUID runId, String storyId, String idempotencyKey, boolean posted) {
        jdbc.update("""
                INSERT INTO audit_event
                    (id, run_id, actor, action, target_system, target_ref, details, created_at)
                VALUES (?, ?, 'qa-agent', 'postStoryComment', 'azure-devops', ?, ?, ?)
                """,
                UUID.randomUUID(),
                runId,
                storyId,
                jsonb(java.util.Map.of("idempotencyKey", idempotencyKey, "posted", posted)),
                Timestamp.from(Instant.now()));
    }

    public Optional<RunDetails> find(UUID runId) {
        List<RunDetails> runs = jdbc.query("""
                SELECT id, story_id, status, triggered_by, temporal_workflow_id, started_at, completed_at, summary
                FROM workflow_run WHERE id = ?
                """,
                (rs, row) -> mapRun(rs, steps(runId), drafts(runId), dataSets(runId)),
                runId);
        return runs.stream().findFirst();
    }

    public List<RunDetails> list(String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query("""
                    SELECT id, story_id, status, triggered_by, temporal_workflow_id, started_at, completed_at, summary
                    FROM workflow_run ORDER BY started_at DESC
                    """,
                    (rs, row) -> mapRun(rs, List.of(), List.of(), List.of()));
        }
        return jdbc.query("""
                SELECT id, story_id, status, triggered_by, temporal_workflow_id, started_at, completed_at, summary
                FROM workflow_run WHERE status = ? ORDER BY started_at DESC
                """,
                (rs, row) -> mapRun(rs, List.of(), List.of(), List.of()),
                status);
    }

    private List<StepView> steps(UUID runId) {
        return jdbc.query("""
                SELECT id, step_name, agent, status, input, output, error, started_at, completed_at
                FROM agent_step WHERE run_id = ? ORDER BY started_at
                """,
                (rs, row) -> new StepView(
                        rs.getObject("id", UUID.class),
                        rs.getString("step_name"),
                        rs.getString("agent"),
                        rs.getString("status"),
                        tree(rs.getObject("input")),
                        tree(rs.getObject("output")),
                        rs.getString("error"),
                        instant(rs, "started_at"),
                        instant(rs, "completed_at")),
                runId);
    }

    private List<DraftView> drafts(UUID runId) {
        return jdbc.query("""
                SELECT id, key, external_ref, status, content
                FROM test_case_draft WHERE run_id = ? ORDER BY key
                """,
                (rs, row) -> new DraftView(
                        rs.getObject("id", UUID.class),
                        rs.getString("key"),
                        rs.getString("external_ref"),
                        rs.getString("status"),
                        tree(rs.getObject("content"))),
                runId);
    }

    private List<DataSetView> dataSets(UUID runId) {
        return jdbc.query("""
                SELECT id, test_case_key, provisioned, spec
                FROM test_data_set WHERE run_id = ? ORDER BY test_case_key
                """,
                (rs, row) -> new DataSetView(
                        rs.getObject("id", UUID.class),
                        rs.getString("test_case_key"),
                        rs.getBoolean("provisioned"),
                        tree(rs.getObject("spec"))),
                runId);
    }

    private RunDetails mapRun(ResultSet rs, List<StepView> steps, List<DraftView> drafts, List<DataSetView> dataSets) throws SQLException {
        return new RunDetails(
                rs.getObject("id", UUID.class),
                rs.getString("story_id"),
                rs.getString("status"),
                rs.getString("triggered_by"),
                rs.getString("temporal_workflow_id"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"),
                tree(rs.getObject("summary")),
                steps,
                drafts,
                dataSets);
    }

    private void publish(UUID stepId, String status, String error) {
        jdbc.query("""
                SELECT id, run_id, step_name FROM agent_step WHERE id = ?
                """,
                (rs, row) -> {
                    events.publish(new StepEvent(
                            rs.getObject("run_id", UUID.class).toString(),
                            rs.getObject("id", UUID.class).toString(),
                            rs.getString("step_name"),
                            status,
                            error));
                    return 1;
                },
                stepId);
    }

    private PGobject jsonb(Object value) {
        if (value == null) {
            return null;
        }
        try {
            PGobject column = new PGobject();
            column.setType("jsonb");
            column.setValue(json.writeValueAsString(value));
            return column;
        } catch (JsonProcessingException | SQLException exception) {
            throw new IntegrationException("Could not store the run record.", exception);
        }
    }

    private JsonNode tree(Object value) {
        if (value == null) {
            return null;
        }
        String text = value instanceof PGobject column ? column.getValue() : value.toString();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return json.readTree(text);
        } catch (JsonProcessingException exception) {
            return json.getNodeFactory().textNode(text);
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record RunDetails(
            UUID id,
            String storyId,
            String status,
            String triggeredBy,
            String temporalWorkflowId,
            Instant startedAt,
            Instant completedAt,
            JsonNode summary,
            List<StepView> steps,
            List<DraftView> drafts,
            List<DataSetView> dataSets
    ) {
    }

    public record StepView(
            UUID id,
            String stepName,
            String agent,
            String status,
            JsonNode input,
            JsonNode output,
            String error,
            Instant startedAt,
            Instant completedAt
    ) {
    }

    public record DraftView(UUID id, String key, String externalRef, String status, JsonNode content) {
    }

    public record DataSetView(UUID id, String testCaseKey, boolean provisioned, JsonNode spec) {
    }
}
