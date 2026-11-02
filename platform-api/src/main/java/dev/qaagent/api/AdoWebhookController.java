package dev.qaagent.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.orchestrator.run.RunAlreadyStartedException;
import dev.qaagent.orchestrator.run.RunService;
import dev.qaagent.orchestrator.run.RunStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/ado")
public class AdoWebhookController {

    static final String SECRET_HEADER = "X-ADO-Webhook-Secret";

    private final RunService runs;
    private final String expectedSecret;

    public AdoWebhookController(RunService runs,
                                @Value("${qaagent.ado.webhook-secret:}") String expectedSecret) {
        this.runs = runs;
        this.expectedSecret = expectedSecret == null ? "" : expectedSecret;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> accept(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody JsonNode body) {
        if (!matches(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "The webhook secret does not match."));
        }
        String storyId = storyId(body);
        String state = state(body);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("storyId", storyId);
        if (storyId.isBlank() || !"Ready".equalsIgnoreCase(state)) {
            response.put("status", "ignored");
            response.put("message", "The work item is not a story moving to Ready.");
            return ResponseEntity.ok(response);
        }
        try {
            RunStore.RunDetails run = runs.start(storyId, "ado-webhook");
            response.put("status", "started");
            response.put("runId", run.id().toString());
            return ResponseEntity.accepted().body(response);
        } catch (RunAlreadyStartedException exception) {
            response.put("status", "ignored");
            response.put("message", exception.getMessage());
            response.put("runId", exception.runId().toString());
            return ResponseEntity.ok(response);
        } catch (StoryNotFoundException exception) {
            response.put("status", "ignored");
            response.put("message", exception.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    private boolean matches(String provided) {
        if (expectedSecret.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedSecret.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    static String storyId(JsonNode body) {
        if (body == null) {
            return "";
        }
        if (body.hasNonNull("storyId")) {
            return body.get("storyId").asText("").trim();
        }
        JsonNode resource = body.path("resource");
        if (resource.hasNonNull("workItemId")) {
            return resource.get("workItemId").asText("").trim();
        }
        if (resource.has("id") && !resource.get("id").isNull()) {
            return resource.get("id").asText("").trim();
        }
        return "";
    }

    static String state(JsonNode body) {
        if (body == null) {
            return "";
        }
        if (body.hasNonNull("state")) {
            return body.get("state").asText("").trim();
        }
        JsonNode resource = body.path("resource");
        String changed = resource.path("fields").path("System.State").asText("");
        if (!changed.isBlank()) {
            return changed.trim();
        }
        return resource.path("revision").path("fields").path("System.State").asText("").trim();
    }
}
