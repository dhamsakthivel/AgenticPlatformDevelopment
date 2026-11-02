package dev.qaagent.app;

import dev.qaagent.agents.prompt.PromptProperties;
import dev.qaagent.agents.prompt.PromptRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptConfigurationTest {

    @Test
    void eachShippedAgentHasAnActivePromptVersion() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        PromptProperties prompts = Binder.get(environment)
                .bind("qaagent.prompts", Bindable.of(PromptProperties.class))
                .orElseThrow(IllegalStateException::new);
        PromptRegistry registry = new PromptRegistry(prompts);

        assertThat(registry.version("story-analysis")).isEqualTo("v1");
        assertThat(registry.version("test-design")).isEqualTo("v1");
        assertThat(registry.render("test-design", Map.of(
                "storyId", "ENR-002",
                "title", "Add a spouse",
                "description", "A subscriber adds a spouse.",
                "acceptanceCriteria", "AC1",
                "analysis", "none",
                "context", "none",
                "similarCases", "none",
                "schema", "{}")))
                .contains("ENR-002");
    }
}
