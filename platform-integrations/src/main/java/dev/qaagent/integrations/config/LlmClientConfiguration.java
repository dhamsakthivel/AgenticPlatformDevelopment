package dev.qaagent.integrations.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.LlmClient;
import dev.qaagent.integrations.llm.ResolvingLlmClient;
import dev.qaagent.knowledge.embed.LlmCallLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OpenAiChatSettings.class, LlmClientProperties.class})
public class LlmClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LlmClientConfiguration.class);

    @Bean
    LlmClient llmClient(OpenAiChatSettings openai, LlmClientProperties llm, PhiRedactor redactor,
                        LlmCallLogger calls, ObjectMapper json) {
        log.info("Each story review reads OPENAI_API_KEY and OPENAI_CHAT_MODEL from the process and from .env.");
        return new ResolvingLlmClient(openai, llm, redactor, calls, json);
    }
}
