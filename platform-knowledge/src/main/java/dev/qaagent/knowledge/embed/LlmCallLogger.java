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
        String runIdText = MDC.get("runId");
        String stepIdText = MDC.get("stepId");
        log.info("llm_call purpose=embed model={} inputTokens={} latencyMs={} success={} runId={} stepId={}",
                model, inputTokens, latencyMs, success, runIdText, stepIdText);
        jdbc.update("""
                INSERT INTO llm_call
                    (id, run_id, step_id, purpose, model, input_tokens, output_tokens, latency_ms, cost_usd, success, created_at)
                VALUES (?, ?, ?, 'embed', ?, ?, 0, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                parseUuid(runIdText),
                parseUuid(stepIdText),
                model,
                inputTokens,
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
