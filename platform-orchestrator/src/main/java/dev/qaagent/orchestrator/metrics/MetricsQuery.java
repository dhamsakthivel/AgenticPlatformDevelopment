package dev.qaagent.orchestrator.metrics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class MetricsQuery {

    private static final String WINDOW = """
            created_at >= COALESCE(CAST(? AS timestamptz), '-infinity'::timestamptz)
            AND created_at < COALESCE(CAST(? AS timestamptz), 'infinity'::timestamptz)
            """;

    private final JdbcTemplate jdbc;

    public MetricsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CostReport report(Instant from, Instant to) {
        Timestamp start = stamp(from);
        Timestamp end = stamp(to);
        List<DayCost> byDay = jdbc.query("""
                SELECT to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day,
                       COALESCE(SUM(input_tokens), 0) AS input_tokens,
                       COALESCE(SUM(output_tokens), 0) AS output_tokens,
                       COALESCE(SUM(cost_usd), 0) AS cost_usd
                FROM llm_call
                WHERE %s
                GROUP BY 1
                ORDER BY 1
                """.formatted(WINDOW), this::day, start, end);
        List<PurposeCost> byPurpose = jdbc.query("""
                SELECT COALESCE(purpose, 'unknown') AS purpose,
                       COUNT(*) AS calls,
                       COALESCE(SUM(input_tokens), 0) AS input_tokens,
                       COALESCE(SUM(output_tokens), 0) AS output_tokens,
                       COALESCE(SUM(cost_usd), 0) AS cost_usd
                FROM llm_call
                WHERE %s
                GROUP BY 1
                ORDER BY cost_usd DESC, purpose
                """.formatted(WINDOW), this::purpose, start, end);
        List<RunCost> byRun = jdbc.query("""
                SELECT run_id,
                       COALESCE(SUM(input_tokens), 0) AS input_tokens,
                       COALESCE(SUM(output_tokens), 0) AS output_tokens,
                       COALESCE(SUM(cost_usd), 0) AS cost_usd
                FROM llm_call
                WHERE run_id IS NOT NULL AND %s
                GROUP BY run_id
                """.formatted(WINDOW), this::run, start, end);
        Totals totals = jdbc.queryForObject("""
                SELECT COALESCE(AVG(total_tokens), 0) AS average_tokens,
                       COUNT(*) AS runs
                FROM (
                    SELECT run_id,
                           SUM(COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0)) AS total_tokens
                    FROM llm_call
                    WHERE run_id IS NOT NULL AND %s
                    GROUP BY run_id
                ) per_run
                """.formatted(WINDOW), (rs, row) -> new Totals(rs.getDouble("average_tokens"), rs.getInt("runs")), start, end);
        Decisions decisions = jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE d->>'action' = 'APPROVE') AS approved,
                       COUNT(*) FILTER (WHERE d->>'action' = 'REJECT') AS rejected,
                       COUNT(*) FILTER (WHERE d->>'action' = 'EDIT') AS edited
                FROM approval a
                CROSS JOIN LATERAL jsonb_array_elements(COALESCE(a.decision->'decisions', '[]'::jsonb)) d
                WHERE a.status = 'DECIDED'
                  AND a.decided_at >= COALESCE(CAST(? AS timestamptz), '-infinity'::timestamptz)
                  AND a.decided_at < COALESCE(CAST(? AS timestamptz), 'infinity'::timestamptz)
                """, (rs, row) -> new Decisions(rs.getInt("approved"), rs.getInt("rejected"), rs.getInt("edited")), start, end);
        return new CostReport(
                byDay,
                byPurpose,
                byRun,
                totals == null ? 0 : totals.averageTokens(),
                totals == null ? 0 : totals.runs(),
                decisions == null ? 0 : decisions.approved(),
                decisions == null ? 0 : decisions.rejected(),
                decisions == null ? 0 : decisions.edited());
    }

    public List<LlmCallView> forRun(UUID runId) {
        return jdbc.query("""
                SELECT step_id, purpose, model, input_tokens, output_tokens, cost_usd, latency_ms, success
                FROM llm_call
                WHERE run_id = ?
                ORDER BY created_at
                """, (rs, row) -> new LlmCallView(
                rs.getObject("step_id", UUID.class),
                rs.getString("purpose"),
                rs.getString("model"),
                rs.getInt("input_tokens"),
                rs.getInt("output_tokens"),
                money(rs, "cost_usd"),
                rs.getInt("latency_ms"),
                rs.getBoolean("success")), runId);
    }

    private DayCost day(ResultSet rs, int row) throws SQLException {
        return new DayCost(rs.getString("day"), rs.getLong("input_tokens"), rs.getLong("output_tokens"), money(rs, "cost_usd"));
    }

    private PurposeCost purpose(ResultSet rs, int row) throws SQLException {
        return new PurposeCost(
                rs.getString("purpose"),
                rs.getLong("calls"),
                rs.getLong("input_tokens"),
                rs.getLong("output_tokens"),
                money(rs, "cost_usd"));
    }

    private RunCost run(ResultSet rs, int row) throws SQLException {
        return new RunCost(
                rs.getObject("run_id", UUID.class),
                rs.getLong("input_tokens"),
                rs.getLong("output_tokens"),
                money(rs, "cost_usd"));
    }

    private static BigDecimal money(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Timestamp stamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public record CostReport(
            List<DayCost> byDay,
            List<PurposeCost> byPurpose,
            List<RunCost> byRun,
            double averageTokensPerRun,
            int runsWithModelCalls,
            int approved,
            int rejected,
            int edited,
            double acceptanceRate
    ) {
        public CostReport(
                List<DayCost> byDay,
                List<PurposeCost> byPurpose,
                List<RunCost> byRun,
                double averageTokensPerRun,
                int runsWithModelCalls,
                int approved,
                int rejected,
                int edited
        ) {
            this(
                    byDay,
                    byPurpose,
                    byRun,
                    averageTokensPerRun,
                    runsWithModelCalls,
                    approved,
                    rejected,
                    edited,
                    rate(approved, rejected, edited));
        }

        private static double rate(int approved, int rejected, int edited) {
            int decided = approved + rejected + edited;
            if (decided == 0) {
                return 0;
            }
            return (approved + edited) / (double) decided;
        }
    }

    public record DayCost(String day, long inputTokens, long outputTokens, BigDecimal costUsd) {
    }

    public record PurposeCost(String purpose, long calls, long inputTokens, long outputTokens, BigDecimal costUsd) {
    }

    public record RunCost(UUID runId, long inputTokens, long outputTokens, BigDecimal costUsd) {
    }

    public record LlmCallView(
            UUID stepId,
            String purpose,
            String model,
            int inputTokens,
            int outputTokens,
            BigDecimal costUsd,
            int latencyMs,
            boolean success
    ) {
    }

    private record Totals(double averageTokens, int runs) {
    }

    private record Decisions(int approved, int rejected, int edited) {
    }
}
