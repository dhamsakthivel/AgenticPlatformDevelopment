package dev.qaagent.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.agents.design.TestDesignAgent;
import dev.qaagent.agents.prompt.PromptProperties;
import dev.qaagent.agents.prompt.PromptRegistry;
import dev.qaagent.core.design.TestDesignInput;
import dev.qaagent.core.design.TestDesignOutput;
import dev.qaagent.core.port.LlmClient;
import dev.qaagent.core.story.RetrievedChunk;
import dev.qaagent.core.story.StoryAnalysisOutput;
import dev.qaagent.integrations.config.LlmClientProperties;
import dev.qaagent.integrations.llm.LlmPrices;
import dev.qaagent.integrations.llm.OpenAiClients;
import dev.qaagent.integrations.llm.OpenAiLlmClient;
import dev.qaagent.knowledge.embed.LlmCallLogger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("eval")
class TestDesignEvalTest {

    private static final ZoneId HALIFAX = ZoneId.of("America/Halifax");

    @Test
    void scoresGoldenDesignsAgainstTheModel() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_CHAT_MODEL");
        assumeTrue(apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank(),
                "Set OPENAI_API_KEY and OPENAI_CHAT_MODEL to score the test designer.");

        Path root = repoRoot();
        List<Path> cases;
        try (Stream<Path> paths = Files.list(root.resolve("evals/test-design"))) {
            cases = paths.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        assertThat(cases).hasSize(6);

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(OpenAiClients.api(null, apiKey))
                    .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                    .build();
            LlmClientProperties prices = new LlmClientProperties("classpath:llm-prices.yml", 0.2);
            LlmClient llm = new OpenAiLlmClient(ChatClient.create(chatModel), model, prices.temperatureValue(),
                    new dev.qaagent.core.phi.PhiRedactor(), new LlmCallLogger(jdbc),
                    LlmPrices.load(prices.priceTableLocation()), new ObjectMapper());
            TestDesignAgent agent = new TestDesignAgent(
                    new PromptRegistry(new PromptProperties(Map.of("test-design", "v1"))), llm);

            ObjectMapper json = new ObjectMapper();
            List<CaseResult> results = new ArrayList<>();
            MDC.put("runId", UUID.randomUUID().toString());
            try {
                for (Path file : cases) {
                    results.add(score(agent, jdbc, json, json.readTree(file.toFile())));
                }
            } finally {
                MDC.clear();
            }
            Path report = writeReport(root, model, results);
            long passed = results.stream().filter(CaseResult::passed).count();
            assertThat(passed * 1.0 / results.size())
                    .as("pass rate written to " + report)
                    .isGreaterThanOrEqualTo(0.8);
        }
    }

    private static CaseResult score(TestDesignAgent agent, JdbcTemplate jdbc, ObjectMapper json, JsonNode spec) {
        JsonNode story = spec.get("story");
        String storyId = story.path("id").asText();
        List<String> criteria = new ArrayList<>();
        story.path("acceptanceCriteria").forEach(node -> criteria.add(node.asText()));
        List<RetrievedChunk> context = new ArrayList<>();
        for (JsonNode chunk : spec.path("retrievedContext")) {
            context.add(new RetrievedChunk(
                    chunk.path("chunkId").asText(),
                    chunk.path("source").asText(),
                    chunk.path("title").asText(),
                    chunk.path("text").asText()));
        }
        List<TestDesignInput.SimilarCase> similar = new ArrayList<>();
        for (JsonNode item : spec.path("similarCases")) {
            similar.add(new TestDesignInput.SimilarCase(
                    item.path("id").asText(), item.path("title").asText(), item.path("summary").asText()));
        }
        TestDesignInput input = new TestDesignInput(
                storyId,
                story.path("title").asText(),
                story.path("description").asText(),
                criteria,
                json.convertValue(spec.get("analysis"), StoryAnalysisOutput.class),
                context,
                similar);
        Totals before = totals(jdbc);
        try {
            TestDesignOutput output = agent.design(input);
            Totals used = totals(jdbc).minus(before);
            List<String> problems = problems(output, spec.get("expected"));
            return new CaseResult(fileId(spec), problems.isEmpty(), output.testCases().size(),
                    used.inputTokens, used.outputTokens, used.cost, String.join("; ", problems));
        } catch (RuntimeException exception) {
            Totals used = totals(jdbc).minus(before);
            return new CaseResult(fileId(spec), false, 0,
                    used.inputTokens, used.outputTokens, used.cost,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    private static String fileId(JsonNode spec) {
        String duplicate = spec.path("expected").path("mustDuplicateOf").asText("");
        String storyId = spec.path("story").path("id").asText();
        return duplicate.isBlank() ? storyId : storyId + " duplicate";
    }

    private static List<String> problems(TestDesignOutput output, JsonNode expected) {
        List<String> problems = new ArrayList<>();
        int minCases = expected.path("minCases").asInt(1);
        if (output.testCases().size() < minCases) {
            problems.add("cases " + output.testCases().size() + " < " + minCases);
        }
        for (JsonNode type : expected.path("mustIncludeTypes")) {
            String name = type.asText();
            boolean found = output.testCases().stream().anyMatch(item -> item.type() != null && item.type().name().equals(name));
            if (!found) {
                problems.add("missing type " + name);
            }
        }
        for (JsonNode criterion : expected.path("mustCoverCriteria")) {
            String id = criterion.asText();
            boolean found = output.testCases().stream().anyMatch(item -> item.linkedCriteria().contains(id));
            if (!found) {
                problems.add("missing criterion " + id);
            }
        }
        String text = output.testCases().stream()
                .map(item -> item.title() + " " + item.steps().stream()
                        .map(step -> step.action() + " " + step.expected())
                        .reduce("", (left, right) -> left + " " + right))
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);
        for (JsonNode phrase : expected.path("mustMentionAnyOf")) {
            if (!text.contains(phrase.asText().toLowerCase(Locale.ROOT))) {
                problems.add("missing \"" + phrase.asText() + "\"");
            }
        }
        String duplicate = expected.path("mustDuplicateOf").asText("");
        if (!duplicate.isBlank() && output.testCases().stream().noneMatch(item -> duplicate.equals(item.possibleDuplicateOf()))) {
            problems.add("no case marked duplicate of " + duplicate);
        }
        return problems;
    }

    private static Path writeReport(Path root, String model, List<CaseResult> results) throws Exception {
        ZonedDateTime now = ZonedDateTime.now(HALIFAX);
        Path directory = root.resolve("evals/reports");
        Files.createDirectories(directory);
        Path report = directory.resolve("test-design-"
                + now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")) + ".md");
        long passed = results.stream().filter(CaseResult::passed).count();
        int input = results.stream().mapToInt(CaseResult::inputTokens).sum();
        int output = results.stream().mapToInt(CaseResult::outputTokens).sum();
        BigDecimal cost = results.stream().map(CaseResult::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder text = new StringBuilder();
        text.append("# Test design eval\n\n");
        text.append("Ran at ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")))
                .append(" with model `").append(model).append("`.\n\n");
        text.append("| Case | Pass | Cases | Input tokens | Output tokens | Cost USD | Notes |\n");
        text.append("|---|---|---:|---:|---:|---:|---|\n");
        for (CaseResult result : results) {
            text.append("| ").append(result.storyId())
                    .append(" | ").append(result.passed() ? "yes" : "no")
                    .append(" | ").append(result.cases())
                    .append(" | ").append(result.inputTokens())
                    .append(" | ").append(result.outputTokens())
                    .append(" | ").append(result.cost().toPlainString())
                    .append(" | ").append(result.notes().replace("|", "/"))
                    .append(" |\n");
        }
        text.append("\nPass rate: ").append(passed).append(" / ").append(results.size())
                .append(String.format(Locale.US, " (%.0f%%)%n", 100.0 * passed / results.size()));
        text.append("Total input tokens: ").append(input).append('\n');
        text.append("Total output tokens: ").append(output).append('\n');
        text.append("Total cost USD: ").append(cost.toPlainString()).append('\n');
        Files.writeString(report, text.toString());
        return report;
    }

    private static Totals totals(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(input_tokens), 0), coalesce(sum(output_tokens), 0), coalesce(sum(cost_usd), 0)
                FROM llm_call
                """, (rs, row) -> new Totals(rs.getInt(1), rs.getInt(2), rs.getBigDecimal(3)));
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int hop = 0; hop < 4 && dir != null; hop++) {
            if (Files.isDirectory(dir.resolve("evals"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not find evals/ from " + Path.of("").toAbsolutePath());
    }

    private record Totals(int inputTokens, int outputTokens, BigDecimal cost) {
        Totals minus(Totals earlier) {
            return new Totals(inputTokens - earlier.inputTokens, outputTokens - earlier.outputTokens,
                    cost.subtract(earlier.cost));
        }
    }

    private record CaseResult(String storyId, boolean passed, int cases, int inputTokens, int outputTokens,
                              BigDecimal cost, String notes) {
    }
}
