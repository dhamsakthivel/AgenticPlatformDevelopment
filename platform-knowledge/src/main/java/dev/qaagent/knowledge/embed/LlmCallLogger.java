package dev.qaagent.knowledge.embed;

import dev.qaagent.core.IntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
public class LlmCallLogger {

    private static final Logger log = LoggerFactory.getLogger(LlmCallLogger.class);

    private final JdbcTemplate jdbc;

    public LlmCallLogger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String model, int inputTokens, long latencyMs, BigDecimal costUsd, boolean success) {
        record("embed", model, null, null, inputTokens, 0, latencyMs, costUsd, success);
    }

    public void record(String purpose, String model, String promptId, String promptVersion,
                       int inputTokens, int outputTokens, long latencyMs, BigDecimal costUsd, boolean success) {
        String runIdText = MDC.get("runId");
        String stepIdText = MDC.get("stepId");
        log.info("llm_call purpose={} model={} prompt={}@{} inputTokens={} outputTokens={} latencyMs={} success={} runId={} stepId={}",
                purpose, model, promptId, promptVersion, inputTokens, outputTokens, latencyMs, success, runIdText, stepIdText);
        jdbc.update("""
                INSERT INTO llm_call
                    (id, run_id, step_id, purpose, model, prompt_id, prompt_version,
                     input_tokens, output_tokens, latency_ms, cost_usd, success, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                parseUuid(runIdText),
                parseUuid(stepIdText),
                purpose,
                model,
                promptId,
                promptVersion,
                inputTokens,
                outputTokens,
                (int) Math.min(latencyMs, Integer.MAX_VALUE),
                costUsd,
                success,
                Timestamp.from(Instant.now()));
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

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    public static BigDecimal cost(int tokens, double usdPerMillion) {
        if (tokens <= 0 || usdPerMillion <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens).multiply(BigDecimal.valueOf(usdPerMillion))
                .divide(BigDecimal.valueOf(1_000_000), 8, java.math.RoundingMode.HALF_UP);
    }
}
