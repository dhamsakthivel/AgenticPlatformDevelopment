package dev.qaagent.agents.prompt;

import dev.qaagent.core.IntegrationException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptRegistry {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[a-zA-Z0-9_-]+}}");

    private final PromptProperties prompts;

    public PromptRegistry(PromptProperties prompts) {
        this.prompts = prompts;
    }

    public String version(String agent) {
        String version = prompts.version(agent);
        if (version.isBlank()) {
            throw new IntegrationException("No prompt version is configured for " + agent + ".");
        }
        return version;
    }

    public String render(String agent, Map<String, String> variables) {
        String version = version(agent);
        String path = "prompts/" + agent + "/" + version + ".md";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IntegrationException("Prompt file was not found at " + path + ".");
        }
        try {
            return renderTemplate(resource.getContentAsString(StandardCharsets.UTF_8), variables);
        } catch (IOException exception) {
            throw new IntegrationException("Could not read prompt " + path + ".", exception);
        }
    }

    public String renderTemplate(String template, Map<String, String> variables) {
        String text = template == null ? "" : template;
        List<String> keys = new ArrayList<>(variables == null ? List.of() : variables.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        for (String key : keys) {
            String value = variables.get(key);
            text = text.replace("{{" + key + "}}", value == null ? "" : value);
        }
        Matcher leftover = PLACEHOLDER.matcher(text);
        if (leftover.find()) {
            throw new IntegrationException("Prompt is missing a value for " + leftover.group() + ".");
        }
        return text;
    }
}
