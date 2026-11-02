package dev.qaagent.integrations.ado;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.core.story.Story;
import dev.qaagent.integrations.workitem.SeedStory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdoWorkItemClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void readsHtmlAcceptanceCriteriaAndTheLastAreaSegment() {
        wireMock.stubFor(get(urlPathEqualTo("/Enrollment/_apis/wit/workitems/42"))
                .withQueryParam("api-version", equalTo("7.1"))
                .willReturn(okJson("""
                        {
                          "id": 42,
                          "fields": {
                            "System.Title": "Add a spouse",
                            "System.Description": "<p>A subscriber.</p>",
                            "System.State": "New",
                            "System.AreaPath": "Enrollment\\\\enrollment",
                            "Microsoft.VSTS.Common.AcceptanceCriteria": "<ul><li>Spouse is stored</li><li>Outcome is ACCEPTED</li></ul>"
                          }
                        }
                        """)));

        Story story = client().getStory("42");

        assertThat(story.title()).isEqualTo("Add a spouse");
        assertThat(story.description()).isEqualTo("A subscriber.");
        assertThat(story.module()).isEqualTo("enrollment");
        assertThat(story.acceptanceCriteria()).containsExactly("Spouse is stored", "Outcome is ACCEPTED");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/Enrollment/_apis/wit/workitems/42"))
                .withHeader("Authorization", equalTo(basic())));
    }

    @Test
    void missingWorkItemIsAStoryNotFound() {
        wireMock.stubFor(get(urlPathEqualTo("/Enrollment/_apis/wit/workitems/missing"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client().getStory("missing"))
                .isInstanceOf(StoryNotFoundException.class)
                .hasMessage("Story not found: missing");
    }

    @Test
    void commentUsesTheMarkdownPreviewApi() {
        wireMock.stubFor(post(urlPathEqualTo("/Enrollment/_apis/wit/workItems/42/comments"))
                .willReturn(okJson("{\"id\":7}")));

        client().addComment("42", "Analysis is ready");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/Enrollment/_apis/wit/workItems/42/comments"))
                .withQueryParam("format", equalTo("markdown"))
                .withQueryParam("api-version", equalTo("7.1-preview.4"))
                .withRequestBody(equalTo("{\"text\":\"Analysis is ready\"}")));
    }

    @Test
    void linkIsAHyperlinkPatch() {
        wireMock.stubFor(patch(urlPathEqualTo("/Enrollment/_apis/wit/workitems/42"))
                .willReturn(okJson("{\"id\":42}")));

        client().addLink("42", "http://localhost:8091/tc/9", "TC1");

        wireMock.verify(patchRequestedFor(urlPathEqualTo("/Enrollment/_apis/wit/workitems/42"))
                .withHeader("Content-Type", equalTo("application/json-patch+json"))
                .withRequestBody(equalTo("[{\"op\":\"add\",\"path\":\"/relations/-\",\"value\":{\"rel\":\"Hyperlink\",\"url\":\"http://localhost:8091/tc/9\",\"attributes\":{\"comment\":\"TC1\"}}}]")));
    }

    @Test
    void ensureUserStorySkipsATitleThatAlreadyExists() {
        wireMock.stubFor(post(urlPathEqualTo("/Enrollment/_apis/wit/wiql"))
                .willReturn(okJson("{\"workItems\":[{\"id\":55}]}")));

        String id = client().ensureUserStory(sample());

        assertThat(id).isEqualTo("55");
        wireMock.verify(0, postRequestedFor(urlPathMatching("/Enrollment/_apis/wit/workitems/.*")));
    }

    @Test
    void ensureUserStoryCreatesAUserStoryWhenTheTitleIsNew() {
        wireMock.stubFor(post(urlPathEqualTo("/Enrollment/_apis/wit/wiql"))
                .willReturn(okJson("{\"workItems\":[]}")));
        wireMock.stubFor(post(urlPathMatching("/Enrollment/_apis/wit/workitems/.*"))
                .willReturn(okJson("{\"id\":1001}")));

        String id = client().ensureUserStory(sample());

        assertThat(id).isEqualTo("1001");
        wireMock.verify(postRequestedFor(urlPathMatching("/Enrollment/_apis/wit/workitems/\\$User.*"))
                .withHeader("Content-Type", equalTo("application/json-patch+json"))
                .withQueryParam("api-version", equalTo("7.1")));
    }

    @Test
    void acceptanceCriteriaFallsBackToLines() {
        assertThat(AdoWorkItemClient.acceptanceCriteria("One\nTwo")).containsExactly("One", "Two");
        assertThat(AdoWorkItemClient.plainText("<div>Two &amp; three</div>")).isEqualTo("Two & three");
    }

    private AdoWorkItemClient client() {
        return new AdoWorkItemClient(wireMock.getRuntimeInfo().getHttpBaseUrl(), "Enrollment", "secret");
    }

    private static String basic() {
        return "Basic " + Base64.getEncoder().encodeToString(":secret".getBytes());
    }

    private static SeedStory sample() {
        return new SeedStory(
                "ENR-001",
                "Add a spouse as a dependent",
                "Store the spouse.",
                List.of("Spouse is stored"),
                "New",
                "enrollment",
                false);
    }
}
