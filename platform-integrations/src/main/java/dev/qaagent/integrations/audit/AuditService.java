package dev.qaagent.integrations.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.IntegrationException;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(String action, String targetSystem, String targetRef, Map<String, ?> details) {
        String runIdText = MDC.get("runId");
        String stepIdText = MDC.get("stepId");
        UUID runId = parseUuid(runIdText);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (details != null) {
            payload.putAll(details);
        }
        if (stepIdText != null && !stepIdText.isBlank()) {
            payload.put("stepId", stepIdText);
        }
        log.info("audit action={} targetSystem={} targetRef={} runId={} stepId={}",
                action, targetSystem, targetRef, runIdText, stepIdText);
        jdbc.update("""
                INSERT INTO audit_event
                    (id, run_id, actor, action, target_system, target_ref, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                runId,
                "qa-agent",
                action,
                targetSystem,
                targetRef,
                jsonb(payload),
                Timestamp.from(Instant.now()));
    }

    private PGobject jsonb(Map<String, ?> payload) {
        try {
            PGobject value = new PGobject();
            value.setType("jsonb");
            value.setValue(json.writeValueAsString(payload));
            return value;
        } catch (JsonProcessingException | SQLException exception) {
            throw new IntegrationException("Could not write the audit event.", exception);
        }
    }

    private static UUID parseUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
