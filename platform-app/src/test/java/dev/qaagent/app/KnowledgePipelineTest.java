package dev.qaagent.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.EmbeddingPort;
import dev.qaagent.core.port.TestManagementPort;
import dev.qaagent.core.port.WorkItemPort;
import dev.qaagent.core.story.Story;
import dev.qaagent.core.testcase.ExternalTestCase;
import dev.qaagent.core.testcase.ExternalTestCaseRef;
import dev.qaagent.core.testcase.TestCaseDraft;
import dev.qaagent.core.testcase.TestCaseStatus;
import dev.qaagent.core.testcase.TestCaseStep;
import dev.qaagent.knowledge.config.KnowledgeProperties;
import dev.qaagent.knowledge.config.RetrievalProperties;
import dev.qaagent.knowledge.embed.DeterministicEmbeddingAdapter;
import dev.qaagent.knowledge.embed.LlmCallLogger;
import dev.qaagent.knowledge.ingest.KnowledgeService;
import dev.qaagent.knowledge.retrieve.HybridRetriever;
import dev.qaagent.knowledge.retrieve.RankedChunk;
import dev.qaagent.knowledge.store.ChunkStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class KnowledgePipelineTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private JdbcTemplate jdbc;
    private KnowledgeService knowledge;

    @BeforeEach
    void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE documents, llm_call CASCADE");
        PhiRedactor redactor = new PhiRedactor();
        EmbeddingPort embeddings = new DeterministicEmbeddingAdapter(redactor, new LlmCallLogger(jdbc), 1536);
        ChunkStore store = new ChunkStore(jdbc, new ObjectMapper());
        HybridRetriever retriever = new HybridRetriever(embeddings, store, redactor);
        Path root = repoRoot();
        KnowledgeProperties properties = new KnowledgeProperties(
                root.resolve("knowledge-seed").toString(),
                root.resolve("scripts/seed/stories.json").toString(),
                32,
                0.02);
        RetrievalProperties retrieval = new RetrievalProperties(Map.of(
                "story-analysis",
                new RetrievalProperties.Profile(List.of("knowledge-seed", "upload", "story"), null, "enrollment", 8)));
        knowledge = new KnowledgeService(redactor, embeddings, store, retriever, properties, retrieval,
                new CatalogStories(), new OneTestCase(), new ObjectMapper());
    }

    @Test
    void hybridSearchRanksTheMatchingRuleAndHonorsFilters() {
        byte[] spouse = """
                # Spouse rule
                SPOUSE-RULE
                spouse dependent spouse dependent spouse dependent
                """.getBytes(StandardCharsets.UTF_8);
        knowledge.ingestUpload("spouse.md", spouse, "Spouse rule", "example-benefit-plan", "enrollment", "upload");
        knowledge.ingestUpload("retro.md", """
                # Retroactive window
                RETRO-RULE
                retroactive termination beyond thirty calendar days is rejected
                """.getBytes(StandardCharsets.UTF_8), "Retroactive window", "example-benefit-plan", "enrollment", "upload");
        knowledge.ingestUpload("claims.md", """
                # Claims
                CLAIMS-RULE
                spouse dependent spouse dependent
                """.getBytes(StandardCharsets.UTF_8), "Claims", "example-benefit-plan", "claims", "upload");

        List<RankedChunk> hits = knowledge.search("spouse dependent", null, null, "enrollment", null, 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().text()).contains("SPOUSE-RULE");
        assertThat(hits).extracting(RankedChunk::text).noneMatch(text -> text.contains("CLAIMS-RULE"));
        assertThat(hits).allMatch(hit -> "enrollment".equals(hit.module()));

        List<RankedChunk> profileHits = knowledge.search("spouse dependent", null, null, null, "story-analysis", 5);
        assertThat(profileHits).extracting(RankedChunk::text).noneMatch(text -> text.contains("CLAIMS-RULE"));

        Long chunks = jdbc.queryForObject("SELECT count(*) FROM chunks", Long.class);
        KnowledgeService.IngestedDocument again = knowledge.ingestUpload(
                "spouse.md", spouse, "Spouse rule", "example-benefit-plan", "enrollment", "upload");
        assertThat(again.skippedUnchanged()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chunks", Long.class)).isEqualTo(chunks);

        knowledge.ingestUpload("phi.md",
                "Notify pat.example@example.test that the spouse dependent rule applies.".getBytes(StandardCharsets.UTF_8),
                "Notice", "example-benefit-plan", "enrollment", "upload");
        String stored = jdbc.queryForObject("""
                SELECT c.text FROM chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE d.uri = 'phi.md'
                """, String.class);
        assertThat(stored).contains("[EMAIL]").doesNotContain("pat.example");

        Long calls = jdbc.queryForObject(
                "SELECT count(*) FROM llm_call WHERE model = 'local-token-hash' AND success = true AND cost_usd = 0",
                Long.class);
        assertThat(calls).isGreaterThan(0);
    }

    @Test
    void reindexLoadsSeedDocsAndKeepsEachStoryAsOneChunk() {
        KnowledgeService.ReindexReport report = knowledge.reindex();

        assertThat(report.warnings()).isEmpty();
        assertThat(report.ingested()).isEqualTo(23);
        assertThat(report.chunks()).isGreaterThanOrEqualTo(23);

        Long storyDocs = jdbc.queryForObject("SELECT count(*) FROM documents WHERE source = 'story'", Long.class);
        Long storyChunks = jdbc.queryForObject("""
                SELECT count(*) FROM chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE d.source = 'story'
                """, Long.class);
        assertThat(storyDocs).isEqualTo(18L);
        assertThat(storyChunks).isEqualTo(storyDocs);
        Integer longestStory = jdbc.queryForObject("""
                SELECT max(length(c.text)) FROM chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE d.source = 'story'
                """, Integer.class);
        assertThat(longestStory).isGreaterThan(4000);

        Long testCaseChunks = jdbc.queryForObject("""
                SELECT count(*) FROM chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE d.source = 'test-case'
                """, Long.class);
        assertThat(testCaseChunks).isEqualTo(1L);

        KnowledgeService.ReindexReport second = knowledge.reindex();
        assertThat(second.ingested()).isZero();
        assertThat(second.skippedUnchanged()).isEqualTo(23);
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int hop = 0; hop < 4 && dir != null; hop++) {
            if (Files.isDirectory(dir.resolve("knowledge-seed"))
                    && Files.isRegularFile(dir.resolve("scripts/seed/stories.json"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not find the repository root from " + Path.of("").toAbsolutePath());
    }

    private static final class CatalogStories implements WorkItemPort {
        @Override
        public Story getStory(String storyId) {
            return new Story(storyId, "Add a spouse " + storyId,
                    "x".repeat(5000) + " spouse dependent",
                    List.of("The spouse is covered."), "New", "enrollment");
        }

        @Override
        public void addComment(String storyId, String markdown) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addLink(String storyId, String url, String label) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class OneTestCase implements TestManagementPort {
        @Override
        public List<ExternalTestCase> findByModule(String module) {
            TestCaseDraft draft = new TestCaseDraft(
                    "TC-SPOUSE",
                    "Spouse enrolls",
                    TestCaseDraft.Type.POSITIVE,
                    TestCaseDraft.Priority.P1,
                    List.of("y".repeat(5000)),
                    List.of(new TestCaseStep("Add the spouse", "The spouse is active")),
                    List.of(),
                    List.of("Spouse is covered"));
            return List.of(new ExternalTestCase(
                    new ExternalTestCaseRef("1", "TC-SPOUSE", "http://localhost/tc"),
                    module,
                    TestCaseStatus.DRAFT,
                    draft));
        }

        @Override
        public ExternalTestCaseRef createDraftTestCase(String storyId, TestCaseDraft draft) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateTestCase(ExternalTestCaseRef ref, TestCaseDraft draft) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setStatus(ExternalTestCaseRef ref, TestCaseStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void attachNote(ExternalTestCaseRef ref, String text) {
            throw new UnsupportedOperationException();
        }
    }
}
