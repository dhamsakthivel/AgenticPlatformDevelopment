package dev.qaagent.integrations.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.llm.LlmRequest;
import dev.qaagent.core.llm.LlmResult;
import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.LlmClient;
import dev.qaagent.knowledge.embed.LlmCallLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public final class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final ChatClient chatClient;
    private final String model;
    private final double temperature;
    private final PhiRedactor redactor;
    private final LlmCallLogger calls;
    private final LlmPrices prices;
    private final JsonSchemaChecker schemas;

    public OpenAiLlmClient(ChatClient chatClient, String model, double temperature, PhiRedactor redactor,
                           LlmCallLogger calls, LlmPrices prices, ObjectMapper json) {
        this.chatClient = chatClient;
        this.model = model;
        this.temperature = temperature;
        this.redactor = redactor;
        this.calls = calls;
        this.prices = prices;
        this.schemas = new JsonSchemaChecker(json);
        if (locksTemperature(model)) {
            log.info("{} only accepts temperature 1, so the configured value {} is replaced.", model, temperature);
        }
    }

    /**
     * Some models, including gpt-5-mini, reject every temperature except 1.
     * Spring AI otherwise sends its own default of 0.7, which those models also reject.
     */
    static boolean locksTemperature(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String name = model.toLowerCase(Locale.ROOT);
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int colon = name.lastIndexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        return name.startsWith("gpt-5")
                || name.startsWith("o1")
                || name.startsWith("o3")
                || name.startsWith("o4");
    }

    @Override
    public <T> LlmResult<T> structured(LlmRequest request, Class<T> type, String schemaId) {
        String redacted = redactor.redact(request.content());
        Attempt first = call(request, redacted);
        List<String> errors = schemas.errors(schemaId, first.raw());
        logCall(request, first, errors.isEmpty());
        if (!errors.isEmpty()) {
            log.info("schema validation failed for {}. Retrying once.", schemaId);
            String repair = redacted + """

                    The previous JSON failed validation:
                    %s
                    Return one corrected JSON object and nothing else.
                    """.formatted(bullet(errors));
            Attempt second = call(request, redactor.redact(repair));
            errors = schemas.errors(schemaId, second.raw());
            logCall(request, second, errors.isEmpty());
            if (!errors.isEmpty()) {
                throw new IntegrationException("The model returned JSON that does not match schema " + schemaId + ".");
            }
            first = second;
        }
        T value = schemas.convert(first.raw(), type);
        return new LlmResult<>(value, model, first.inputTokens(), first.outputTokens(), first.latencyMs(), first.cost());
    }

    private Attempt call(LlmRequest request, String content) {
        long started = System.nanoTime();
        try {
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                    .model(model)
                    .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
            options.temperature(locksTemperature(model) ? 1.0 : temperature);
            ChatResponse response = chatClient.prompt()
                    .user(content)
                    .options(options.build())
                    .call()
                    .chatResponse();
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new IntegrationException("The model returned an empty response.");
            }
            String raw = response.getResult().getOutput().getText();
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            int inputTokens = tokens(usage == null ? null : usage.getPromptTokens(), content);
            int outputTokens = tokens(usage == null ? null : usage.getCompletionTokens(), raw);
            return new Attempt(raw, inputTokens, outputTokens, latencyMs, prices.cost(model, inputTokens, outputTokens));
        } catch (IntegrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            calls.record(purpose(request), model, request.promptId(), request.promptVersion(),
                    LlmCallLogger.estimateTokens(content), 0, latencyMs, BigDecimal.ZERO, false);
            throw new IntegrationException("The model request failed.", exception);
        }
    }

    private void logCall(LlmRequest request, Attempt attempt, boolean success) {
        calls.record(purpose(request), model, request.promptId(), request.promptVersion(),
                attempt.inputTokens(), attempt.outputTokens(), attempt.latencyMs(), attempt.cost(), success);
    }

    private static String purpose(LlmRequest request) {
        return request.purpose() == null || request.purpose().isBlank() ? "chat" : request.purpose();
    }

    private static int tokens(Integer reported, String text) {
        if (reported != null && reported >= 0) {
            return reported;
        }
        return LlmCallLogger.estimateTokens(text);
    }

    private static String bullet(List<String> errors) {
        StringBuilder text = new StringBuilder();
        for (String error : errors) {
            text.append("- ").append(error).append('\n');
        }
        return text.toString().trim();
    }

    private record Attempt(String raw, int inputTokens, int outputTokens, long latencyMs, BigDecimal cost) {
    }
}
