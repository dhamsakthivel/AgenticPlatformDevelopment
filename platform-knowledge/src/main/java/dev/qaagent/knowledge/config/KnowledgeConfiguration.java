package dev.qaagent.knowledge.config;

import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.EmbeddingPort;
import dev.qaagent.knowledge.embed.DeterministicEmbeddingAdapter;
import dev.qaagent.knowledge.embed.LlmCallLogger;
import dev.qaagent.knowledge.embed.OpenAiEmbeddingAdapter;
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
        if (!openai.hasApiKey()) {
            log.info("OPENAI_API_KEY is empty. Embeddings use the local token stand-in {}.",
                    DeterministicEmbeddingAdapter.MODEL);
            return new DeterministicEmbeddingAdapter(redactor, calls, openai.dimensions());
        }
        return new OpenAiEmbeddingAdapter(openai.apiKey(), openai.model(), openai.dimensions(),
                redactor, calls, knowledge);
    }
}
