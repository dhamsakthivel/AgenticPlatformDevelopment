package dev.qaagent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.orchestrator.run.RunAlreadyStartedException;
import dev.qaagent.orchestrator.run.RunNotFoundException;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RunControllerTest {

    @Mock
    private RunService runs;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RunController(runs, new RunEventStream())).build();
    }

    @Test
    void startReturnsTheNewRun() throws Exception {
        UUID runId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(runs.start("ENR-001", "sam")).thenReturn(details(runId, "RUNNING"));

        mvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(
                                new RunController.StartRun("ENR-001", "sam"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(runId.toString()))
                .andExpect(jsonPath("$.storyId").value("ENR-001"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void missingStoryIsNotFound() throws Exception {
        when(runs.start("ENR-999", null)).thenThrow(new StoryNotFoundException("ENR-999"));

        mvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storyId\":\"ENR-999\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Story not found: ENR-999"));
    }

    @Test
    void aSecondRunForTheSameStoryConflicts() throws Exception {
        UUID existing = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(runs.start("ENR-001", null)).thenThrow(new RunAlreadyStartedException(existing, "ENR-001"));

        mvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storyId\":\"ENR-001\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.runId").value(existing.toString()));
    }

    @Test
    void getReturnsSteps() throws Exception {
        UUID runId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(runs.get(runId)).thenReturn(details(runId, "WAITING_FOR_APPROVAL"));

        mvc.perform(get("/api/runs/{id}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_APPROVAL"))
                .andExpect(jsonPath("$.steps[0].stepName").value("fetchStory"));
    }

    @Test
    void unknownRunIsNotFound() throws Exception {
        UUID runId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(runs.get(runId)).thenThrow(new RunNotFoundException(runId.toString()));

        mvc.perform(get("/api/runs/{id}", runId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listPassesTheStatusFilter() throws Exception {
        when(runs.list("RUNNING")).thenReturn(List.of());

        mvc.perform(get("/api/runs").param("status", "RUNNING"))
                .andExpect(status().isOk());
        verify(runs).list("RUNNING");
    }

    private static RunStore.RunDetails details(UUID runId, String status) {
        return new RunStore.RunDetails(
                runId,
                "ENR-001",
                status,
                "sam",
                runId.toString(),
                Instant.parse("2026-11-01T20:00:00Z"),
                null,
                null,
                List.of(new RunStore.StepView(
                        UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                        "fetchStory",
                        null,
                        "SUCCEEDED",
                        null,
                        null,
                        null,
                        Instant.parse("2026-11-01T20:00:01Z"),
                        Instant.parse("2026-11-01T20:00:02Z"))),
                List.of(),
                List.of());
    }
}
