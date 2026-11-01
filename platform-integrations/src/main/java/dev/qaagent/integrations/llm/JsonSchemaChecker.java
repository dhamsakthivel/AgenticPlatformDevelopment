package dev.qaagent.integrations.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.schema.SchemaFiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class JsonSchemaChecker {

    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final ObjectMapper json;
    private final ConcurrentHashMap<String, JsonSchema> schemas = new ConcurrentHashMap<>();

    public JsonSchemaChecker(ObjectMapper json) {
        this.json = json;
    }

    public List<String> errors(String schemaId, String raw) {
        JsonNode node;
        try {
            node = json.readTree(unwrap(raw));
        } catch (Exception exception) {
            return List.of("Response was not a JSON object.");
        }
        if (node == null || !node.isObject()) {
            return List.of("Response was not a JSON object.");
        }
        List<String> messages = new ArrayList<>();
        for (var message : schema(schemaId).validate(node)) {
            messages.add(message.getMessage());
        }
        return List.copyOf(messages);
    }

    public <T> T convert(String raw, Class<T> type) {
        try {
            return json.convertValue(json.readTree(unwrap(raw)), type);
        } catch (Exception exception) {
            throw new IntegrationException("The model JSON could not be read as " + type.getSimpleName() + ".", exception);
        }
    }

    private JsonSchema schema(String schemaId) {
        return schemas.computeIfAbsent(schemaId, id -> {
            try {
                return factory.getSchema(json.readTree(SchemaFiles.text(id)));
            } catch (IntegrationException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IntegrationException("Could not load JSON schema " + id + ".", exception);
            }
        });
    }

    static String unwrap(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLine = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLine < 0 || lastFence <= firstLine) {
            return trimmed;
        }
        return trimmed.substring(firstLine + 1, lastFence).trim();
    }
}
