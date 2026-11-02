package dev.qaagent.integrations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaagent.llm")
public record LlmClientProperties(String priceTable, Double temperature) {

    public double temperatureValue() {
        return temperature == null || temperature < 0 ? 0.2 : temperature;
    }

    public String priceTableLocation() {
        return priceTable == null || priceTable.isBlank() ? "classpath:llm-prices.yml" : priceTable;
    }
}
