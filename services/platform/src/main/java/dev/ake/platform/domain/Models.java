package dev.ake.platform.domain;

import java.time.Instant;
import java.util.List;

public final class Models {

    private Models() {}

    public record Citation(String documentId, String title, String section, String excerpt, double score) {}

    public record Prediction(int points, double confidence, List<String> risks, List<String> rationale, int neighbors) {}

    public record KnowledgeDocument(
            String id,
            String title,
            String source,
            String text,
            List<String> tags,
            String ingestStatus,
            int chunks,
            Instant createdAt) {

        public KnowledgeDocument withIngest(String status, int chunkCount) {
            return new KnowledgeDocument(id, title, source, text, tags, status, chunkCount, createdAt);
        }

        public Summary summary() {
            return new Summary(id, title, source, tags, text == null ? 0 : text.length(), ingestStatus, chunks, createdAt);
        }

        public record Summary(
                String id,
                String title,
                String source,
                List<String> tags,
                int characters,
                String ingestStatus,
                int chunks,
                Instant createdAt) {}
    }

    public record UserStory(
            String id,
            String title,
            String persona,
            String action,
            String benefit,
            String description,
            List<String> acceptanceCriteria,
            String priority,
            String status,
            int points,
            double confidence,
            List<String> risks,
            List<String> rationale,
            List<Citation> citations,
            List<String> notes,
            String model,
            Instant createdAt) {

        public UserStory update(String nextTitle, String nextStatus, String nextPriority, Integer nextPoints) {
            return new UserStory(
                    id,
                    blank(nextTitle) ? title : nextTitle.trim(),
                    persona,
                    action,
                    benefit,
                    description,
                    acceptanceCriteria,
                    blank(nextPriority) ? priority : nextPriority.trim().toLowerCase(),
                    blank(nextStatus) ? status : nextStatus.trim().toLowerCase(),
                    nextPoints == null ? points : nextPoints,
                    confidence,
                    risks,
                    rationale,
                    citations,
                    notes,
                    model,
                    createdAt);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }

    public record AuditEntry(Instant at, String action, String subject, String detail) {}

    public record Insights(
            int stories,
            double averagePoints,
            double averageConfidence,
            List<RiskCount> risks,
            List<UserStory> readySlice,
            List<UserStory> watchlist,
            List<String> notes) {

        public record RiskCount(String risk, int count) {}
    }

    public record CoachNote(boolean enabled, String suggestion) {}

    public record SystemReport(
            String platform,
            String storage,
            AgentStatus agent,
            Security security,
            List<AuditEntry> audit) {

        public record AgentStatus(
                String status,
                String llm,
                String embeddings,
                String vector,
                int chunks,
                boolean langsmith,
                String langsmithProject,
                List<String> mcp) {}

        public record Security(boolean apiKeyRequired, String header, String browserHoldsKey, List<String> controls) {}
    }
}
