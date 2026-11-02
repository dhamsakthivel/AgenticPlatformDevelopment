package dev.qaagent.integrations.ado;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.qaagent.integrations.audit.AuditService;
import dev.qaagent.integrations.config.AdoProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AzureDevOpsAdapterTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final AuditService audit = mock(AuditService.class);

    @Test
    void writesAreAuditedAfterAzureDevOpsAcceptsThem() {
        wireMock.stubFor(post(urlPathEqualTo("/Enrollment/_apis/wit/workItems/42/comments"))
                .willReturn(okJson("{\"id\":1}")));
        wireMock.stubFor(patch(urlPathEqualTo("/Enrollment/_apis/wit/workitems/42"))
                .willReturn(okJson("{\"id\":42}")));
        AzureDevOpsAdapter adapter = adapter();

        adapter.addComment("42", "hello");
        adapter.addLink("42", "http://localhost/tc/1", "TC1");

        verify(audit).record(eq("addComment"), eq("azure-devops"), eq("42"),
                argThat(details -> "sandbox".equals(details.get("mode"))));
        verify(audit).record(eq("addLink"), eq("azure-devops"), eq("42"),
                argThat(details -> "http://localhost/tc/1".equals(details.get("url"))
                        && "TC1".equals(details.get("label"))));
    }

    @Test
    void aFailedWriteIsNotAudited() {
        wireMock.stubFor(post(urlPathEqualTo("/Enrollment/_apis/wit/workItems/42/comments"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> adapter().addComment("42", "hello"))
                .isInstanceOf(dev.qaagent.core.IntegrationException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void sandboxRefusesToStartWithoutAPat() {
        assertThatThrownBy(() -> new AzureDevOpsAdapter(
                new AdoProperties("http://localhost", "Enrollment", " ", ""),
                audit))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADO_PAT");
    }

    private AzureDevOpsAdapter adapter() {
        return new AzureDevOpsAdapter(new AdoProperties(
                wireMock.getRuntimeInfo().getHttpBaseUrl(),
                "Enrollment",
                "secret",
                ""), audit);
    }
}
