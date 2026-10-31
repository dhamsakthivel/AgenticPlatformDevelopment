package dev.ake.platform.agent;

import dev.ake.platform.domain.Models.Citation;
import dev.ake.platform.domain.Models.Prediction;
import dev.ake.platform.domain.Models.SystemReport.AgentStatus;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ake.agent.mode", havingValue = "stub")
public class StubAgentClient implements AgentClient {

    @Override
    public IngestResult ingest(String id, String title, String text, String source, List<String> tags) {
        return new IngestResult(id, 1, "stub");
    }

    @Override
    public void delete(String documentId) {}

    @Override
    public QueryResult query(String query, int topK, List<String> documentIds) {
        return new QueryResult(
                List.of(new Citation("seed-harbor", "Harbor Field Service brief", "Dispatch board", query, 0.5)),
                "stub");
    }

    @Override
    public Draft generate(String prompt, List<String> documentIds) {
        return new Draft(
                "Notify technician of a window change",
                "field technician",
                "see the new arrival window when dispatch moves my job",
                "I do not drive to the old site",
                "As a field technician, I want to see the new arrival window when dispatch moves my job, so that I do not drive to the old site.",
                List.of(
                        "Given a window change over 30 minutes, when the job is mine, then I see the new window and site.",
                        "The customer message does not include my phone number.",
                        "If I do not acknowledge within 10 minutes, dispatch sees an unacknowledged badge."),
                "high",
                List.of(new Citation("seed-harbor", "Harbor Field Service brief", "Same-day window changes", prompt, 0.8)),
                List.of("Stub draft for tests."),
                new Prediction(5, 0.6, List.of("integration"), List.of("Notification and acknowledgement."), 0),
                "stub");
    }

    @Override
    public Prediction estimate(String title, String description, List<String> acceptanceCriteria) {
        return new Prediction(3, 0.5, List.of(), List.of("Stub estimate."), 0);
    }

    @Override
    public AgentStatus status() {
        return new AgentStatus("up", "stub", "stub", "stub", 1, false, "", List.of("/mcp"));
    }
}
