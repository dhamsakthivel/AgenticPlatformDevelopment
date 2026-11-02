package dev.qaagent.core.llm;

import java.math.BigDecimal;

public record LlmResult<T>(
        T value,
        String model,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        BigDecimal costUsd
) {
}
