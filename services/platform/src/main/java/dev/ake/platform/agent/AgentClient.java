package dev.ake.platform.agent;

import dev.ake.platform.domain.Models.Citation;
import dev.ake.platform.domain.Models.Prediction;
import dev.ake.platform.domain.Models.SystemReport.AgentStatus;
import java.util.List;

public interface AgentClient {

    IngestResult ingest(String id, String title, String text, String source, List<String> tags);

    void delete(String documentId);

    QueryResult query(String query, int topK, List<String> documentIds);

    Draft generate(String prompt, List<String> documentIds);

    Prediction estimate(String title, String description, List<String> acceptanceCriteria);

    AgentStatus status();

    record IngestResult(String id, int chunks, String backend) {}

    record QueryResult(List<Citation> citations, String backend) {}

    record Draft(
            String title,
            String persona,
            String action,
            String benefit,
            String description,
            List<String> acceptanceCriteria,
            String priority,
            List<Citation> citations,
            List<String> notes,
            Prediction prediction,
            String model) {}
}
