package dev.qaagent.integrations.llm;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

public final class OpenAiClients {

    private OpenAiClients() {
    }

    public static OpenAiApi api(String baseUrl, String apiKey) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        RestClient.Builder rest = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(http));
        OpenAiApi.Builder builder = OpenAiApi.builder().apiKey(apiKey).restClientBuilder(rest);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
