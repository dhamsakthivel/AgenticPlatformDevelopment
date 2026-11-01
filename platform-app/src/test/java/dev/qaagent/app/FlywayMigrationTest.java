package dev.qaagent.app;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Test
    void domainMigrationCreatesTablesAndVectorIndexes() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            Set<String> tables = new HashSet<>();
            try (ResultSet rows = statement.executeQuery("""
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                    """)) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
            assertThat(tables).contains(
                    "workflow_run",
                    "agent_step",
                    "approval",
                    "test_case_draft",
                    "test_data_set",
                    "documents",
                    "chunks",
                    "llm_call",
                    "audit_event");

            assertThat(indexDef(statement, "chunks_embedding_hnsw_idx")).contains("hnsw").contains("vector_cosine_ops");
            assertThat(indexDef(statement, "chunks_tsv_gin_idx")).containsIgnoringCase("gin");
            assertThat(indexDef(statement, "workflow_run_story_id_idx")).contains("story_id");
            assertThat(indexDef(statement, "agent_step_run_id_idx")).contains("run_id");

            statement.executeUpdate("""
                    INSERT INTO audit_event (id, actor, action, target_system, target_ref, details, created_at)
                    VALUES ('11111111-1111-1111-1111-111111111111', 'qa-agent', 'probe', 'qtest', '1', '{}'::jsonb, now())
                    """);
            try (ResultSet count = statement.executeQuery("SELECT count(*) FROM audit_event")) {
                assertThat(count.next()).isTrue();
                assertThat(count.getInt(1)).isEqualTo(1);
            }
        }
    }

    private static String indexDef(Statement statement, String name) throws Exception {
        try (ResultSet rows = statement.executeQuery(
                "SELECT indexdef FROM pg_indexes WHERE indexname = '" + name + "'")) {
            assertThat(rows.next()).as("index " + name).isTrue();
            return rows.getString(1);
        }
    }
}
