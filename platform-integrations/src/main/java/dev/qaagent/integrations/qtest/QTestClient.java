package dev.qaagent.integrations.qtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.testcase.ExternalTestCase;
import dev.qaagent.core.testcase.ExternalTestCaseRef;
import dev.qaagent.core.testcase.TestCaseDraft;
import dev.qaagent.core.testcase.TestCaseStatus;
import dev.qaagent.core.testcase.TestCaseStep;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * qTest Manager API v3. Authorization is the bearer token from the project's API and SDK page.
 * Test cases are created and listed with parent_id / parentId, which is the module id.
 */
public final class QTestClient {

    private final HttpClient http;
    private final ObjectMapper json;
    private final String baseUrl;
    private final String token;
    private final String projectId;
    private final String moduleId;
    private final String statusFieldId;

    public QTestClient(String baseUrl, String token, String projectId, String moduleId, String statusFieldId) {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build(), new ObjectMapper(), baseUrl, token, projectId, moduleId, statusFieldId);
    }

    QTestClient(HttpClient http, ObjectMapper json, String baseUrl, String token,
                String projectId, String moduleId, String statusFieldId) {
        this.http = http;
        this.json = json;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.token = token == null ? "" : token;
        this.projectId = required(projectId, "QTEST_PROJECT_ID");
        this.moduleId = required(moduleId, "QTEST_MODULE_ID");
        this.statusFieldId = statusFieldId == null ? "" : statusFieldId;
    }

    public ExternalTestCaseRef createDraft(String storyId, TestCaseDraft draft) {
        ObjectNode body = caseBody(storyId, draft, TestCaseStatus.DRAFT);
        JsonNode created = send("POST", casesUri(""), body.toString());
        return ref(created);
    }

    public void update(ExternalTestCaseRef ref, TestCaseDraft draft, TestCaseStatus status) {
        ObjectNode body = caseBody(null, draft, status);
        send("PUT", caseUri(ref.testCaseId()), body.toString());
    }

    public TestCaseStatus statusOf(ExternalTestCaseRef ref) {
        return statusOf(send("GET", caseUri(ref.testCaseId()) + "?expandSteps=true", null));
    }

    public void setStatus(ExternalTestCaseRef ref, TestCaseStatus status) {
        JsonNode current = send("GET", caseUri(ref.testCaseId()) + "?expandSteps=true", null);
        ArrayNode properties = json.createArrayNode();
        boolean replaced = false;
        for (JsonNode property : current.path("properties")) {
            ObjectNode copy = property.deepCopy();
            if ("Status".equals(copy.path("field_name").asText())) {
                copy.put("field_value", status.name());
                replaced = true;
            }
            properties.add(copy);
        }
        if (!replaced) {
            ObjectNode property = properties.addObject();
            property.put("field_name", "Status");
            property.put("field_value", status.name());
            if (!statusFieldId.isBlank()) {
                property.put("field_id", Long.parseLong(statusFieldId));
            }
        }
        ObjectNode update = json.createObjectNode();
        update.put("name", current.path("name").asText(""));
        update.put("parent_id", current.path("parent_id").asLong(Long.parseLong(moduleId)));
        update.put("description", current.path("description").asText(""));
        update.put("precondition", current.path("precondition").asText(""));
        update.set("test_steps", current.path("test_steps"));
        update.set("properties", properties);
        send("PUT", caseUri(ref.testCaseId()), update.toString());
    }

    public void addComment(ExternalTestCaseRef ref, String text) {
        ObjectNode body = json.createObjectNode().put("content", text);
        send("POST", caseUri(ref.testCaseId()) + "/comments", body.toString());
    }

    public List<ExternalTestCase> findByModule(String module) {
        String parent = module == null || module.isBlank() ? moduleId : module;
        JsonNode page = send("GET", casesUri("?parentId=" + parent + "&expandSteps=true&size=200"), null);
        List<ExternalTestCase> cases = new ArrayList<>();
        if (page.isArray()) {
            page.forEach(node -> cases.add(toCase(node, parent)));
        }
        return List.copyOf(cases);
    }

    private ObjectNode caseBody(String storyId, TestCaseDraft draft, TestCaseStatus status) {
        ObjectNode body = json.createObjectNode();
        body.put("name", draft.title());
        body.put("parent_id", Long.parseLong(moduleId));
        body.put("description", description(storyId, draft));
        body.put("precondition", String.join("\n", draft.preconditions()));
        ArrayNode steps = body.putArray("test_steps");
        int order = 1;
        for (TestCaseStep step : draft.steps()) {
            ObjectNode item = steps.addObject();
            item.put("description", step.action());
            item.put("expected", step.expected());
            item.put("order", order++);
        }
        ArrayNode properties = body.putArray("properties");
        ObjectNode property = properties.addObject();
        if (!statusFieldId.isBlank()) {
            property.put("field_id", Long.parseLong(statusFieldId));
        }
        property.put("field_name", "Status");
        property.put("field_value", status.name());
        return body;
    }

    private static String description(String storyId, TestCaseDraft draft) {
        StringBuilder text = new StringBuilder();
        if (storyId != null && !storyId.isBlank()) {
            text.append("Story: ").append(storyId).append('\n');
        }
        text.append("Key: ").append(draft.key()).append('\n');
        text.append("Type: ").append(draft.type()).append('\n');
        text.append("Priority: ").append(draft.priority()).append('\n');
        if (!draft.linkedCriteria().isEmpty()) {
            text.append("Linked criteria: ").append(String.join(", ", draft.linkedCriteria())).append('\n');
        }
        if (!draft.dataRequirements().isEmpty()) {
            text.append("Data: ").append(String.join("; ", draft.dataRequirements()));
        }
        return text.toString().trim();
    }

    private ExternalTestCase toCase(JsonNode node, String module) {
        TestCaseStatus status = statusOf(node);
        List<TestCaseStep> steps = new ArrayList<>();
        node.path("test_steps").forEach(step -> steps.add(new TestCaseStep(
                step.path("description").asText(""),
                step.path("expected").asText(""))));
        String key = node.path("pid").asText("");
        TestCaseDraft draft = new TestCaseDraft(
                key,
                node.path("name").asText(""),
                TestCaseDraft.Type.POSITIVE,
                TestCaseDraft.Priority.P2,
                List.of(node.path("precondition").asText("")),
                steps,
                List.of(),
                List.of());
        return new ExternalTestCase(ref(node), module, status, draft);
    }

    private ExternalTestCaseRef ref(JsonNode node) {
        String url = node.path("web_url").asText("");
        return new ExternalTestCaseRef(projectId, node.path("id").asText(), url);
    }

    private TestCaseStatus statusOf(JsonNode node) {
        for (JsonNode property : node.path("properties")) {
            if ("Status".equals(property.path("field_name").asText())) {
                String value = property.path("field_value").asText("DRAFT");
                return TestCaseStatus.valueOf(value);
            }
        }
        return TestCaseStatus.DRAFT;
    }

    private JsonNode send(String method, String path, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (!token.isBlank()) {
            request.header("Authorization", "bearer " + token);
        }
        if ("GET".equals(method)) {
            request.GET();
        } else {
            request.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IntegrationException("qTest " + method + " " + path
                        + " failed with status " + response.statusCode());
            }
            if (response.body() == null || response.body().isBlank()) {
                return json.createObjectNode();
            }
            return json.readTree(response.body());
        } catch (IntegrationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IntegrationException("qTest request failed for " + path, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegrationException("qTest request was interrupted for " + path, exception);
        }
    }

    private String casesUri(String suffix) {
        return "/api/v3/projects/" + projectId + "/test-cases" + suffix;
    }

    private String caseUri(String testCaseId) {
        return casesUri("/" + testCaseId);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IntegrationException("QTEST_BASE_URL is empty.");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IntegrationException(name + " is empty.");
        }
        return value;
    }
}
