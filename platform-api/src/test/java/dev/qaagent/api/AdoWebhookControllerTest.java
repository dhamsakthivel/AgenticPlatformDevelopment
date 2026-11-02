package dev.qaagent.api;

import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.orchestrator.run.RunAlreadyStartedException;
import dev.qaagent.orchestrator.run.RunService;
import dev.qaagent.orchestrator.run.RunStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdoWebhookControllerTest {

    @Mock
    private RunService runs;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AdoWebhookController(runs, "hook-secret")).build();
    }

    @Test
    void rejectsAMissingSecret() throws Exception {
        mvc.perform(post("/api/webhooks/ado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storyId\":\"ENR-001\",\"state\":\"Ready\"}"))
                .andExpect(status().isUnauthorized());
        verify(runs, never()).start("ENR-001", "ado-webhook");
    }

    @Test
    void startsWhenTheStoryMovesToReady() throws Exception {
        UUID runId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(runs.start("42", "ado-webhook")).thenReturn(run(runId));

        mvc.perform(post("/api/webhooks/ado")
                        .header(AdoWebhookController.SECRET_HEADER, "hook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"workitem.updated","resource":{"id":42,"fields":{"System.State":"Ready"}}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("started"))
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.storyId").value("42"));
    }

    @Test
    void ignoresAStoryThatIsNotReady() throws Exception {
        mvc.perform(post("/api/webhooks/ado")
                        .header(AdoWebhookController.SECRET_HEADER, "hook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storyId\":\"ENR-001\",\"state\":\"New\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));
        verify(runs, never()).start("ENR-001", "ado-webhook");
    }

    @Test
    void ignoresAStoryThatIsAlreadyRunning() throws Exception {
        UUID existing = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(runs.start("ENR-001", "ado-webhook"))
                .thenThrow(new RunAlreadyStartedException(existing, "ENR-001"));

        mvc.perform(post("/api/webhooks/ado")
                        .header(AdoWebhookController.SECRET_HEADER, "hook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storyId\":\"ENR-001\",\"state\":\"Ready\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"))
                .andExpect(jsonPath("$.runId").value(existing.toString()));
    }

    @Test
    void ignoresAnUnknownStory() throws Exception {
        when(runs.start("ENR-999", "ado-webhook")).thenThrow(new StoryNotFoundException("ENR-999"));

        mvc.perform(post("/api/webhooks/ado")
                        .header(AdoWebhookController.SECRET_HEADER, "hook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storyId\":\"ENR-999\",\"state\":\"Ready\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));
    }

    private static RunStore.RunDetails run(UUID runId) {
        return new RunStore.RunDetails(
                runId, "42", "RUNNING", "ado-webhook", runId.toString(),
                Instant.parse("2026-11-01T20:00:00Z"), null, null, List.of(), List.of(), List.of());
    }
}
