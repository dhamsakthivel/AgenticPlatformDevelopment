package dev.qaagent.integrations.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.llm.LlmRequest;
import dev.qaagent.core.llm.LlmResult;
import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.story.StoryAnalysisOutput;
import dev.qaagent.knowledge.embed.LlmCallLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiLlmClientTest {

    private static final String CHUNK = "11111111-1111-1111-1111-111111111111";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private JdbcTemplate jdbc;
    private OpenAiLlmClient client;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build());
        jdbc.execute("""
                CREATE TABLE llm_call (
                    id uuid PRIMARY KEY,
                    run_id uuid,
                    step_id uuid,
                    purpose varchar(64),
                    model varchar(128),
                    prompt_id varchar(128),
                    prompt_version varchar(32),
                    input_tokens int,
                    output_tokens int,
                    latency_ms int,
                    cost_usd numeric,
                    success boolean,
                    created_at timestamp NOT NULL
                )
                """);
        ChatClient chat = ChatClient.create(OpenAiChatModel.builder()
                .openAiApi(OpenAiClients.api(wireMock.baseUrl(), "test-key"))
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build());
        client = new OpenAiLlmClient(chat, "gpt-4o-mini", 0.2, new PhiRedactor(), new LlmCallLogger(jdbc),
                LlmPrices.load("classpath:llm-prices.yml"), new ObjectMapper());
    }

    @Test
    void retriesOnceWhenTheJsonDoesNotMatchTheSchema() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario("repair")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(completion("{\"storyId\":\"ENR-001\",\"riskScore\":9}")))
                .willSetStateTo("repaired"));
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario("repair")
                .whenScenarioStateIs("repaired")
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(completion(validAnalysis()))));

        UUID runId = UUID.randomUUID();
        MDC.put("runId", runId.toString());
        try {
            LlmResult<StoryAnalysisOutput> result = client.structured(new LlmRequest(
                    "story-analysis", "story-analysis", "v1",
                    "Contact pat.example@example.test. Return JSON for the story.",
                    ""), StoryAnalysisOutput.class, "story-analysis-output");

            assertThat(result.value().storyId()).isEqualTo("ENR-001");
            assertThat(result.value().riskScore()).isEqualTo(2);
            assertThat(result.model()).isEqualTo("gpt-4o-mini");
            assertThat(result.inputTokens()).isEqualTo(100);
            assertThat(result.outputTokens()).isEqualTo(50);
            assertThat(result.costUsd()).isEqualByComparingTo("0.00004500");
        } finally {
            MDC.clear();
        }

        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/v1/chat/completions"))
                .withRequestBody(notContaining("pat.example@example.test"))
                .withRequestBody(containing("[EMAIL]")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/chat/completions"))
                .withRequestBody(containing("failed validation")));

        Long calls = jdbc.queryForObject("SELECT count(*) FROM llm_call", Long.class);
        assertThat(calls).isEqualTo(2L);
        Long failed = jdbc.queryForObject("SELECT count(*) FROM llm_call WHERE success = false", Long.class);
        assertThat(failed).isEqualTo(1L);
        String prompt = jdbc.queryForObject(
                "SELECT prompt_id || '@' || prompt_version FROM llm_call WHERE success = true", String.class);
        assertThat(prompt).isEqualTo("story-analysis@v1");
        UUID storedRun = jdbc.queryForObject("SELECT run_id FROM llm_call WHERE success = true", UUID.class);
        assertThat(storedRun).isEqualTo(runId);
    }

    @Test
    void failsWhenTheRepairStillDoesNotMatch() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(completion("{\"storyId\":\"ENR-001\",\"riskScore\":9}"))));

        assertThatThrownBy(() -> client.structured(new LlmRequest(
                "story-analysis", "story-analysis", "v1", "Return JSON.", ""),
                StoryAnalysisOutput.class, "story-analysis-output"))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("story-analysis-output");
    }

    @Test
    void unknownModelCostsZero() {
        assertThat(LlmPrices.load("classpath:llm-prices.yml").cost("missing-model", 10, 10))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    private String completion(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "id", "chatcmpl-test",
                "object", "chat.completion",
                "created", 1,
                "model", "gpt-4o-mini",
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", content),
                        "finish_reason", "stop")),
                "usage", Map.of("prompt_tokens", 100, "completion_tokens", 50, "total_tokens", 150)));
    }

    private static String validAnalysis() {
        return """
                {"storyId":"ENR-001","summary":"Add a spouse.","criteria":[{"id":"AC1","text":"Spouse is stored.","testable":true,"issue":null}],"ambiguities":[],"missingScenarios":[],"riskScore":2,"riskReasons":["The criteria name the outcome."],"citations":[{"chunkId":"%s","source":"knowledge-seed"}]}
                """.formatted(CHUNK);
    }
}
