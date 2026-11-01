package dev.qaagent.integrations.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AdoProperties.class, QTestProperties.class, SeedProperties.class})
public class IntegrationConfiguration {
}
