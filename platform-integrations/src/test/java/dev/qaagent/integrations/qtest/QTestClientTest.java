package dev.qaagent.integrations.qtest;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.qaagent.core.testcase.ExternalTestCaseRef;
import dev.qaagent.core.testcase.TestCaseDraft;
import dev.qaagent.core.testcase.TestCaseStatus;
import dev.qaagent.core.testcase.TestCaseStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class QTestClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void createSendsADraftUnderTheConfiguredModule() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v3/projects/1/test-cases"))
                .willReturn(okJson(caseJson("DRAFT"))));

        ExternalTestCaseRef ref = client("token-value").createDraft("ENR-001", draft());

        assertThat(ref.projectId()).isEqualTo("1");
        assertThat(ref.testCaseId()).isEqualTo("9");
        assertThat(ref.url()).isEqualTo("http://mock/tc/9");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/api/v3/projects/1/test-cases"))
                .withHeader("Authorization", equalTo("bearer token-value"))
                .withRequestBody(equalToJson("""
                        {
                          "name": "Spouse is stored",
                          "parent_id": 1,
                          "description": "Story: ENR-001\\nKey: TC1\\nType: POSITIVE\\nPriority: P1\\nLinked criteria: AC1\\nData: spouse aged 30",
                          "precondition": "Subscriber is active",
                          "test_steps": [
                            {"description": "Submit ADD", "expected": "ACCEPTED", "order": 1}
                          ],
                          "properties": [
                            {"field_name": "Status", "field_value": "DRAFT"}
                          ]
                        }
                        """, true, true)));
    }

    @Test
    void blankTokenOmitsTheAuthorizationHeader() {
        wireMock.stubFor(post(urlPathEqualTo("/api/v3/projects/1/test-cases"))
                .willReturn(okJson(caseJson("DRAFT"))));

        client("").createDraft("ENR-001", draft());

        wireMock.verify(postRequestedFor(urlPathEqualTo("/api/v3/projects/1/test-cases"))
                .withoutHeader("Authorization"));
    }

    @Test
    void setStatusKeepsTheExistingSteps() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/projects/1/test-cases/9"))
                .willReturn(okJson(caseJson("DRAFT"))));
        wireMock.stubFor(put(urlPathEqualTo("/api/v3/projects/1/test-cases/9"))
                .willReturn(okJson(caseJson("APPROVED"))));

        client("token-value").setStatus(new ExternalTestCaseRef("1", "9", "http://mock/tc/9"), TestCaseStatus.APPROVED);

        wireMock.verify(putRequestedFor(urlPathEqualTo("/api/v3/projects/1/test-cases/9"))
                .withRequestBody(equalToJson("""
                        {
                          "name": "Spouse is stored",
                          "parent_id": 1,
                          "description": "Key: TC1",
                          "precondition": "Subscriber is active",
                          "test_steps": [
                            {"description": "Submit ADD", "expected": "ACCEPTED", "order": 1}
                          ],
                          "properties": [
                            {"field_name": "Status", "field_value": "APPROVED"}
                          ]
                        }
                        """, true, true)));
    }

    @Test
    void listAndCommentUseTheModuleAndContentFields() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v3/projects/1/test-cases"))
                .withQueryParam("parentId", equalTo("enrollment"))
                .willReturn(okJson("[" + caseJson("DRAFT") + "]")));
        wireMock.stubFor(post(urlPathEqualTo("/api/v3/projects/1/test-cases/9/comments"))
                .willReturn(okJson("{\"id\":3,\"content\":\"note\"}")));
        QTestClient qtest = client("token-value");

        assertThat(qtest.findByModule("enrollment")).singleElement()
                .satisfies(found -> {
                    assertThat(found.status()).isEqualTo(TestCaseStatus.DRAFT);
                    assertThat(found.draft().title()).isEqualTo("Spouse is stored");
                    assertThat(found.draft().steps()).containsExactly(new TestCaseStep("Submit ADD", "ACCEPTED"));
                });
        qtest.addComment(new ExternalTestCaseRef("1", "9", "http://mock/tc/9"), "note");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/api/v3/projects/1/test-cases/9/comments"))
                .withRequestBody(equalTo("{\"content\":\"note\"}")));
    }

    private QTestClient client(String token) {
        return new QTestClient(wireMock.getRuntimeInfo().getHttpBaseUrl(), token, "1", "1", "");
    }

    private static TestCaseDraft draft() {
        return new TestCaseDraft(
                "TC1",
                "Spouse is stored",
                TestCaseDraft.Type.POSITIVE,
                TestCaseDraft.Priority.P1,
                List.of("Subscriber is active"),
                List.of(new TestCaseStep("Submit ADD", "ACCEPTED")),
                List.of("spouse aged 30"),
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
