package dev.qaagent.knowledge.config;

import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.EmbeddingPort;
import dev.qaagent.knowledge.embed.LlmCallLogger;
import dev.qaagent.knowledge.embed.SelectingEmbeddingAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        KnowledgeProperties.class,
        RetrievalProperties.class,
        OpenAiEmbeddingSettings.class
})
public class KnowledgeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeConfiguration.class);

    @Bean
    PhiRedactor phiRedactor() {
        return new PhiRedactor();
    }

    @Bean
    EmbeddingPort embeddingPort(OpenAiEmbeddingSettings openai, KnowledgeProperties knowledge,
                                PhiRedactor redactor, LlmCallLogger calls) {
        log.info("Each search reads OPENAI_API_KEY from the process and from .env.");
        return new SelectingEmbeddingAdapter(openai, knowledge, redactor, calls);
    }
}
