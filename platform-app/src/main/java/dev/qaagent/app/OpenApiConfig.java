package dev.qaagent.app;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI platformOpenApi() {
        return new OpenAPI().info(new Info()
                .title("QA Agentic Platform")
                .description("HTTP API for the QA agentic platform. Phase 0 exposes the health check only.")
                .version("0.1.0"));
    }
}
