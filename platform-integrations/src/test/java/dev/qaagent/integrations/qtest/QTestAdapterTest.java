package dev.qaagent.integrations.qtest;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.testcase.ExternalTestCaseRef;
import dev.qaagent.core.testcase.TestCaseDraft;
import dev.qaagent.core.testcase.TestCaseStatus;
import dev.qaagent.core.testcase.TestCaseStep;
import dev.qaagent.integrations.audit.AuditService;
import dev.qaagent.integrations.config.QTestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class QTestAdapterTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final AuditService audit = mock(AuditService.class);

    @Test
    void externalWritesAreAudited() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v3/projects/1/test-cases"))
                .willReturn(okJson(caseJson("DRAFT"))));
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/projects/1/test-cases/9"))
                .willReturn(okJson(caseJson("DRAFT"))));
        wireMock.stubFor(put(urlPathEqualTo("/api/v3/projects/1/test-cases/9"))
                .willReturn(okJson(caseJson("APPROVED"))));
        wireMock.stubFor(post(urlPathEqualTo("/api/v3/projects/1/test-cases/9/comments"))
                .willReturn(okJson("{\"id\":3,\"content\":\"note\"}")));
        QTestAdapter adapter = adapter();
        ExternalTestCaseRef ref = new ExternalTestCaseRef("1", "9", "http://mock/tc/9");

        ExternalTestCaseRef created = adapter.createDraftTestCase("ENR-001", draft());
        adapter.setStatus(ref, TestCaseStatus.APPROVED);
        adapter.attachNote(ref, "note");

        assertThat(created.testCaseId()).isEqualTo("9");
        verify(audit).record(eq("createDraftTestCase"), eq("qtest"), eq("9"),
                argThat(details -> "ENR-001".equals(details.get("storyId"))
                        && "DRAFT".equals(details.get("status"))));
        verify(audit).record(eq("setStatus"), eq("qtest"), eq("9"),
                argThat(details -> "APPROVED".equals(details.get("status"))));
        verify(audit).record(eq("attachNote"), eq("qtest"), eq("9"),
                argThat(details -> "note".equals(details.get("text"))));
    }

    @Test
    void aRejectedCreateIsNotAudited() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v3/projects/1/test-cases"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter().createDraftTestCase("ENR-001", draft()))
                .isInstanceOf(IntegrationException.class);
        verifyNoInteractions(audit);
    }

    private QTestAdapter adapter() {
        return new QTestAdapter(new QTestProperties(
                wireMock.getRuntimeInfo().getHttpBaseUrl(),
                "",
                "1",
                "1",
                ""), audit);
    }

    private static TestCaseDraft draft() {
        return new TestCaseDraft(
                "TC1",
                "Spouse is stored",
                TestCaseDraft.Type.POSITIVE,
                TestCaseDraft.Priority.P1,
                List.of("Subscriber is active"),
                List.of(new TestCaseStep("Submit ADD", "ACCEPTED")),
                List.of(),
                List.of("AC1"));
    }

    private static String caseJson(String status) {
        return """
                {
                  "id": 9,
                  "pid": "TC-9",
                  "name": "Spouse is stored",
                  "parent_id": 1,
                  "description": "Key: TC1",
                  "precondition": "Subscriber is active",
                  "web_url": "http://mock/tc/9",
                  "properties": [{"field_name": "Status", "field_value": "%s"}],
                  "test_steps": [{"description": "Submit ADD", "expected": "ACCEPTED", "order": 1}]
                }
                """.formatted(status);
    }
}
