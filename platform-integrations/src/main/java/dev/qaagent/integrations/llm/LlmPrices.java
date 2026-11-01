package dev.qaagent.integrations.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.knowledge.embed.LlmCallLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

public final class LlmPrices {

    private static final Logger log = LoggerFactory.getLogger(LlmPrices.class);

    private final Map<String, ModelPrice> models;

    private LlmPrices(Map<String, ModelPrice> models) {
        this.models = models;
    }

    public static LlmPrices load(String location) {
        Resource resource = new DefaultResourceLoader().getResource(location == null ? "" : location);
        if (!resource.exists()) {
            throw new IntegrationException("LLM price table was not found at " + location);
        }
        ObjectMapper yaml = new YAMLMapper();
        try (InputStream in = resource.getInputStream()) {
            PriceFile file = yaml.readValue(in, PriceFile.class);
            Map<String, ModelPrice> models = file == null || file.models() == null ? Map.of() : Map.copyOf(file.models());
            return new LlmPrices(models);
        } catch (IOException exception) {
            throw new IntegrationException("Could not read the LLM price table.", exception);
        }
    }

    public BigDecimal cost(String model, int inputTokens, int outputTokens) {
        ModelPrice price = model == null ? null : models.get(model);
        if (price == null) {
            log.warn("No price is configured for model {}. Cost is recorded as 0. Add it to llm-prices.yml.", model);
            return BigDecimal.ZERO;
        }
        return LlmCallLogger.cost(inputTokens, price.inputUsdPerMillionTokens())
                .add(LlmCallLogger.cost(outputTokens, price.outputUsdPerMillionTokens()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PriceFile(Map<String, ModelPrice> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ModelPrice(
            @JsonProperty("input-usd-per-million-tokens") double inputUsdPerMillionTokens,
            @JsonProperty("output-usd-per-million-tokens") double outputUsdPerMillionTokens
    ) {
    }
}
