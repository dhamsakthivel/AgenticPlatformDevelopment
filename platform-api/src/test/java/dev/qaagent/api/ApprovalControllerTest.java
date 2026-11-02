package dev.qaagent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.orchestrator.run.ApprovalClosedException;
import dev.qaagent.orchestrator.run.ApprovalNotFoundException;
import dev.qaagent.orchestrator.run.ApprovalService;
import dev.qaagent.orchestrator.run.RunStore;
import dev.qaagent.orchestrator.story.ApprovalDecision;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    private static final UUID APPROVAL = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private ApprovalService approvals;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ApprovalController(approvals)).build();
    }

    @Test
    void listsPendingApprovals() throws Exception {
        when(approvals.list("PENDING")).thenReturn(List.of(view()));

        mvc.perform(get("/api/approvals").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].storyId").value("ENR-001"))
                .andExpect(jsonPath("$[0].drafts[0].key").value("TC1"));
        verify(approvals).list("PENDING");
    }

    @Test
    void decisionSignalsTheRun() throws Exception {
        when(approvals.decide(any(), any())).thenReturn(view());

        mvc.perform(post("/api/approvals/{id}/decision", APPROVAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decidedBy":"sam","decisions":[{"key":"TC1","action":"APPROVE","editedCase":null}]}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("signalled"))
                .andExpect(jsonPath("$.approvalId").value(APPROVAL.toString()));
    }

    @Test
    void aMissingDraftIsRejected() throws Exception {
        when(approvals.decide(any(), any()))
                .thenThrow(new IllegalArgumentException("Decide every draft. Missing TC2."));

        mvc.perform(post("/api/approvals/{id}/decision", APPROVAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(new ApprovalDecision("sam", List.of(
                                new ApprovalDecision.CaseDecision("TC1", ApprovalDecision.Action.APPROVE, null))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Decide every draft. Missing TC2."));
    }

    @Test
    void anUnknownApprovalIsNotFound() throws Exception {
        when(approvals.decide(any(), any())).thenThrow(new ApprovalNotFoundException(APPROVAL.toString()));

        mvc.perform(post("/api/approvals/{id}/decision", APPROVAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decidedBy\":\"sam\",\"decisions\":[{\"key\":\"TC1\",\"action\":\"APPROVE\"}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aClosedApprovalConflicts() throws Exception {
        when(approvals.decide(any(), any()))
                .thenThrow(new ApprovalClosedException("This approval is already DECIDED."));

        mvc.perform(post("/api/approvals/{id}/decision", APPROVAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decidedBy\":\"sam\",\"decisions\":[{\"key\":\"TC1\",\"action\":\"APPROVE\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This approval is already DECIDED."));
    }

    private static RunStore.ApprovalView view() {
        return new RunStore.ApprovalView(
                APPROVAL,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "ENR-001",
                "PENDING",
                Instant.parse("2026-11-02T00:30:00Z"),
                null,
                null,
                List.of(new RunStore.DraftView(
                        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                        "TC1",
                        "9",
                        "DRAFT",
                        null)));
    }
}
