package dev.qaagent.agents.config;

import dev.qaagent.agents.prompt.PromptProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PromptProperties.class)
public class AgentConfiguration {
}
