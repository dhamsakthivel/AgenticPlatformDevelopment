package dev.qaagent.core.schema;

import dev.qaagent.core.IntegrationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class SchemaFiles {

    private SchemaFiles() {
    }

    public static String text(String schemaId) {
        if (schemaId == null || !schemaId.matches("[a-z0-9-]+")) {
            throw new IntegrationException("JSON schema id is missing.");
        }
        String path = "/schemas/" + schemaId + ".json";
        try (InputStream in = SchemaFiles.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IntegrationException("No JSON schema named " + schemaId + ".");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IntegrationException("Could not read JSON schema " + schemaId + ".", exception);
        }
    }
}
