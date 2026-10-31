package dev.ake.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ake")
public record AkeProperties(String apiKey, Agent agent, Coach coach) {

    public record Agent(String baseUrl, String apiKey, String mode) {}

    public record Coach(String model) {}
}
