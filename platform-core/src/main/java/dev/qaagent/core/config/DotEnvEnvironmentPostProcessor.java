package dev.qaagent.core.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Makes values in {@code .env} visible to {@code ${OPENAI_API_KEY}} and the other placeholders.
 * Process environment and command-line arguments still win.
 */
public final class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, String> values = DotEnv.fileValues();
        if (!values.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("dotenv", Map.copyOf(values)));
        }
    }
}
