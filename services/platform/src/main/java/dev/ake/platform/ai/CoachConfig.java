package dev.ake.platform.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoachConfig {

    @Bean
    StoryCoach storyCoach(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${ake.coach.model:gpt-4o-mini}") String model) {
        if (apiKey == null || apiKey.isBlank()) {
            return new StoryCoach(null);
        }
        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.2).build())
                .build();
        return new StoryCoach(ChatClient.builder(chatModel).build());
    }
}
