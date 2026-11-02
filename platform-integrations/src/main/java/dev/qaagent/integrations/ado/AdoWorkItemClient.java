package dev.qaagent.integrations.ado;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.core.story.Story;
import dev.qaagent.integrations.workitem.SeedStory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Azure DevOps Work Item Tracking calls.
 * Work items use api-version 7.1. Comments use api-version 7.1-preview.4 with format=markdown.
 */
public final class AdoWorkItemClient {

    static final String WORK_ITEM_API = "7.1";
    static final String COMMENT_API = "7.1-preview.4";

    private static final Pattern LIST_ITEM = Pattern.compile("(?is)<li[^>]*>(.*?)</li>");

    private final HttpClient http;
    private final ObjectMapper json;
    private final String orgUrl;
    private final String project;
    private final String authorization;

    public AdoWorkItemClient(String orgUrl, String project, String pat) {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build(), new ObjectMapper(), orgUrl, project, pat);
    }

    AdoWorkItemClient(HttpClient http, ObjectMapper json, String orgUrl, String project, String pat) {
        this.http = http;
        this.json = json;
        this.orgUrl = stripTrailingSlash(orgUrl);
        this.project = project;
        this.authorization = "Basic " + Base64.getEncoder().encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8));
    }

    public Story getStory(String storyId) {
        JsonNode body = send("GET", workItemUri(storyId), null, "application/json", storyId);
        JsonNode fields = body.path("fields");
        String area = text(fields, "System.AreaPath");
        String module = area.contains("\\") ? area.substring(area.lastIndexOf('\\') + 1) : "enrollment";
        return new Story(
                storyId,
                text(fields, "System.Title"),
                plainText(text(fields, "System.Description")),
                acceptanceCriteria(text(fields, "Microsoft.VSTS.Common.AcceptanceCriteria")),
                text(fields, "System.State"),
                module);
    }

    public void addComment(String storyId, String markdown) {
        ObjectNode payload = json.createObjectNode().put("text", markdown);
        send("POST", commentUri(storyId), payload.toString(), "application/json", storyId);
    }

    public void addLink(String storyId, String url, String label) {
        ArrayNode patch = json.createArrayNode();
        ObjectNode operation = patch.addObject();
        operation.put("op", "add");
        operation.put("path", "/relations/-");
        ObjectNode value = operation.putObject("value");
        value.put("rel", "Hyperlink");
        value.put("url", url);
        value.putObject("attributes").put("comment", label);
        send("PATCH", workItemUri(storyId), patch.toString(), "application/json-patch+json", storyId);
    }

    public String ensureUserStory(SeedStory story) {
        String existing = findIdByTitle(story.title());
        if (existing != null) {
            return existing;
        }
        ArrayNode patch = json.createArrayNode();
        addField(patch, "/fields/System.Title", story.title());
        addField(patch, "/fields/System.Description", story.description());
        addField(patch, "/fields/Microsoft.VSTS.Common.AcceptanceCriteria", htmlList(story.acceptanceCriteria()));
        JsonNode created = send(
                "POST",
                createUri(),
                patch.toString(),
                "application/json-patch+json",
                story.id());
        return created.path("id").asText();
    }

    private String findIdByTitle(String title) {
        ObjectNode query = json.createObjectNode().put("query",
                "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.Title] = '"
                        + title.replace("'", "''") + "'");
        JsonNode response = send(
                "POST",
                collectionUri("/_apis/wit/wiql?api-version=" + WORK_ITEM_API),
                query.toString(),
                "application/json",
                title);
        JsonNode items = response.path("workItems");
        if (!items.isArray() || items.isEmpty()) {
            return null;
        }
        return items.get(0).path("id").asText();
    }

    private void addField(ArrayNode patch, String path, String value) {
        ObjectNode operation = patch.addObject();
        operation.put("op", "add");
        operation.put("path", path);
        operation.put("value", value == null ? "" : value);
    }

    private JsonNode send(String method, URI uri, String body, String contentType, String storyId) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .header("Content-Type", contentType);
        if ("GET".equals(method)) {
            request.GET();
        } else {
            request.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 && "GET".equals(method)) {
                throw new StoryNotFoundException(storyId);
            }
            if (response.statusCode() >= 300) {
                throw new IntegrationException("Azure DevOps " + method + " " + uri.getPath()
                        + " failed with status " + response.statusCode() + ": " + abbreviate(response.body()));
            }
            if (response.body() == null || response.body().isBlank()) {
                return json.createObjectNode();
            }
            return json.readTree(response.body());
        } catch (StoryNotFoundException exception) {
            throw exception;
        } catch (IntegrationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IntegrationException("Azure DevOps request failed for story " + storyId, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegrationException("Azure DevOps request was interrupted for story " + storyId, exception);
        }
    }

    private URI workItemUri(String storyId) {
        return collectionUri("/_apis/wit/workitems/" + encode(storyId) + "?api-version=" + WORK_ITEM_API);
    }

    private URI commentUri(String storyId) {
        return collectionUri("/_apis/wit/workItems/" + encode(storyId)
                + "/comments?format=markdown&api-version=" + COMMENT_API);
    }

    private URI createUri() {
        return collectionUri("/_apis/wit/workitems/$User%20Story?api-version=" + WORK_ITEM_API);
    }

    private URI collectionUri(String pathAndQuery) {
        return URI.create(orgUrl + "/" + encode(project) + pathAndQuery);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static List<String> acceptanceCriteria(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Matcher matcher = LIST_ITEM.matcher(raw);
        List<String> items = new ArrayList<>();
        while (matcher.find()) {
            String text = plainText(matcher.group(1));
            if (!text.isBlank()) {
                items.add(text);
            }
        }
        if (!items.isEmpty()) {
            return List.copyOf(items);
        }
        return plainText(raw).lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    static String plainText(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
    }

    private static String htmlList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<ul>");
        for (String item : items) {
            html.append("<li>").append(item == null ? "" : item).append("</li>");
        }
        return html.append("</ul>").toString();
    }

    private static String text(JsonNode fields, String name) {
        JsonNode node = fields.get(name);
        return node == null || node.isNull() ? "" : node.asText();
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IntegrationException("ADO_ORG_URL is empty. Set it before using the sandbox profile.");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300);
    }
}
