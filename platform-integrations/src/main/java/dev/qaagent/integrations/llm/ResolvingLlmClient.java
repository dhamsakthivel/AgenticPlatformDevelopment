package dev.qaagent.integrations.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.config.DotEnv;
import dev.qaagent.core.llm.LlmRequest;
import dev.qaagent.core.llm.LlmResult;
import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.LlmClient;
import dev.qaagent.integrations.config.LlmClientProperties;
import dev.qaagent.integrations.config.OpenAiChatSettings;
import dev.qaagent.knowledge.embed.LlmCallLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.function.Function;

/**
 * Reads the API key and chat model on every call. A key saved in {@code .env} after startup is used
 * on the next review. The process environment wins over the file, and the file wins over the value
 * bound when the app started.
 */
public final class ResolvingLlmClient implements LlmClient {

    public static final String MISSING_KEY = "Set OPENAI_API_KEY before asking the model to read a story.";
    public static final String MISSING_MODEL =
            "Set OPENAI_CHAT_MODEL before asking the model to read a story. For example, gpt-5-mini.";

    private static final Logger log = LoggerFactory.getLogger(ResolvingLlmClient.class);

    private final Function<String, String> values;
    private final ChatFactory chats;
    private final Object lock = new Object();

    private String activeKey = "";
    private String activeModel = "";
    private LlmClient active;

    public ResolvingLlmClient(OpenAiChatSettings settings, LlmClientProperties llm, PhiRedactor redactor,
                              LlmCallLogger calls, ObjectMapper json) {
        this(name -> DotEnv.resolve(name, configured(settings, name)),
                (apiKey, model) -> openAi(llm, redactor, calls, json, apiKey, model));
    }

    ResolvingLlmClient(Function<String, String> values, ChatFactory chats) {
        this.values = values;
        this.chats = chats;
    }

    @Override
    public <T> LlmResult<T> structured(LlmRequest request, Class<T> type, String schemaId) {
        String apiKey = blank(values.apply("OPENAI_API_KEY"));
        String model = blank(values.apply("OPENAI_CHAT_MODEL"));
        if (apiKey.isEmpty()) {
            throw new IntegrationException(MISSING_KEY);
        }
        if (model.isEmpty()) {
            throw new IntegrationException(MISSING_MODEL);
        }
        return client(apiKey, model).structured(request, type, schemaId);
    }

    private LlmClient client(String apiKey, String model) {
        synchronized (lock) {
            if (active != null && apiKey.equals(activeKey) && model.equals(activeModel)) {
                return active;
            }
            log.info("The next story review will call {}.", model);
            active = chats.create(apiKey, model);
            activeKey = apiKey;
            activeModel = model;
            return active;
        }
    }

    private static LlmClient openAi(LlmClientProperties llm, PhiRedactor redactor, LlmCallLogger calls,
                                    ObjectMapper json, String apiKey, String model) {
        OpenAiChatModel chat = OpenAiChatModel.builder()
                .openAiApi(OpenAiClients.api(null, apiKey))
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build();
        return new OpenAiLlmClient(ChatClient.create(chat), model, llm.temperatureValue(),
                redactor, calls, LlmPrices.load(llm.priceTableLocation()), json);
    }

    private static String configured(OpenAiChatSettings settings, String name) {
        if (settings == null) {
            return "";
        }
        String value = switch (name) {
            case "OPENAI_API_KEY" -> settings.apiKey();
            case "OPENAI_CHAT_MODEL" -> settings.chatModel();
            default -> "";
        };
        return value == null ? "" : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    interface ChatFactory {
        LlmClient create(String apiKey, String model);
    }
}
