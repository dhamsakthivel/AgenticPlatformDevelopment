package dev.qaagent.api;

import dev.qaagent.orchestrator.metrics.MetricsQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MetricsControllerTest {

    @Mock
    private MetricsQuery metrics;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MetricsController(metrics)).build();
    }

    @Test
    void costsReturnsTheReport() throws Exception {
        when(metrics.report(Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z")))
                .thenReturn(new MetricsQuery.CostReport(
                        List.of(new MetricsQuery.DayCost("2026-11-01", 100, 40, new BigDecimal("0.0200"))),
                        List.of(new MetricsQuery.PurposeCost("story-analysis", 1, 80, 40, new BigDecimal("0.0150"))),
                        List.of(),
                        140,
                        1,
                        2,
                        1,
                        0));

        mvc.perform(get("/api/metrics/costs")
                        .param("from", "2026-11-01T00:00:00Z")
                        .param("to", "2026-11-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byDay[0].day").value("2026-11-01"))
                .andExpect(jsonPath("$.byPurpose[0].purpose").value("story-analysis"))
                .andExpect(jsonPath("$.averageTokensPerRun").value(140))
                .andExpect(jsonPath("$.approved").value(2))
                .andExpect(jsonPath("$.acceptanceRate").value(0.6666666666666666));

        verify(metrics).report(Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z"));
    }

    @Test
    void anInvertedRangeIsRejected() throws Exception {
        mvc.perform(get("/api/metrics/costs")
                        .param("from", "2026-11-02T00:00:00Z")
                        .param("to", "2026-11-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The start of the range is after the end."));
    }
}
