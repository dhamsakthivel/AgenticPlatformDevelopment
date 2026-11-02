package dev.qaagent.api;

import dev.qaagent.orchestrator.metrics.MetricsQuery;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsQuery metrics;

    public MetricsController(MetricsQuery metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/costs")
    public MetricsQuery.CostReport costs(@RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        Instant start = parse(from, "from");
        Instant end = parse(to, "to");
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("The start of the range is after the end.");
        }
        return metrics.report(start, end);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> rejected(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }

    private static Instant parse(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(name + " must be an ISO-8601 time, for example 2026-11-01T00:00:00Z.");
        }
    }
}
