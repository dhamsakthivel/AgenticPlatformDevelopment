package dev.qaagent.integrations.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.LlmClient;
import dev.qaagent.integrations.llm.LlmPrices;
import dev.qaagent.integrations.llm.OpenAiClients;
import dev.qaagent.integrations.llm.OpenAiLlmClient;
import dev.qaagent.integrations.llm.UnavailableLlmClient;
import dev.qaagent.knowledge.embed.LlmCallLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
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
        if (!openai.hasApiKey()) {
            log.info("OPENAI_API_KEY is empty. The story analyst stays off until a key is set.");
            return new UnavailableLlmClient("Set OPENAI_API_KEY before asking the model to read a story.");
        }
        if (!openai.hasChatModel()) {
            log.info("OPENAI_CHAT_MODEL is empty. The story analyst stays off until a model name is set.");
            return new UnavailableLlmClient("Set OPENAI_CHAT_MODEL before asking the model to read a story. For example, gpt-5-mini.");
        }
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(OpenAiClients.api(null, openai.apiKey()))
                .defaultOptions(OpenAiChatOptions.builder().model(openai.chatModel()).build())
                .build();
        LlmPrices prices = LlmPrices.load(llm.priceTableLocation());
        return new OpenAiLlmClient(ChatClient.create(model), openai.chatModel(), llm.temperatureValue(),
                redactor, calls, prices, json);
    }
}
