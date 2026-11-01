package dev.qaagent.integrations.workitem;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.story.Story;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class StoryCatalog {

    private static final TypeReference<List<SeedStory>> STORIES = new TypeReference<>() {
    };

    private StoryCatalog() {
    }

    public static List<SeedStory> load(Path path) {
        try {
            return new ObjectMapper().readValue(path.toFile(), STORIES);
        } catch (IOException exception) {
            throw new IntegrationException("Could not read stories from " + path, exception);
        }
    }

    public static Path resolve(String configured) {
        Path direct = Path.of(configured);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path fromParent = Path.of("..").resolve(configured).normalize();
        if (Files.isRegularFile(fromParent)) {
            return fromParent;
        }
        throw new IntegrationException(
                "Stories file not found at " + direct.toAbsolutePath()
                        + ". Run from the repository root, or set qaagent.seed.stories-path.");
    }

    public static Story toStory(SeedStory seed) {
        return new Story(
                seed.id(),
                seed.title(),
                seed.description(),
                seed.acceptanceCriteria(),
                seed.state(),
                seed.module());
    }
}
