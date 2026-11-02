package dev.qaagent.api;

import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.orchestrator.metrics.MetricsQuery;
import dev.qaagent.orchestrator.run.RunAlreadyStartedException;
import dev.qaagent.orchestrator.run.RunNotFoundException;
import dev.qaagent.orchestrator.run.RunService;
import dev.qaagent.orchestrator.run.RunStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunService runs;
    private final RunEventStream events;
    private final MetricsQuery metrics;

    public RunController(RunService runs, RunEventStream events, MetricsQuery metrics) {
        this.runs = runs;
        this.events = events;
        this.metrics = metrics;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunStore.RunDetails start(@RequestBody StartRun request) {
        return runs.start(request == null ? null : request.storyId(), request == null ? null : request.triggeredBy());
    }

    @GetMapping
    public List<RunStore.RunDetails> list(@RequestParam(required = false) String status) {
        return runs.list(status);
    }

    @GetMapping("/{id}")
    public RunStore.RunDetails get(@PathVariable UUID id) {
        return runs.get(id);
    }

    @GetMapping("/{id}/usage")
    public List<MetricsQuery.LlmCallView> usage(@PathVariable UUID id) {
        runs.get(id);
        return metrics.forRun(id);
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID id) {
        RunStore.RunDetails run = runs.get(id);
        return events.subscribe(id, run.steps());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> rejected(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler({StoryNotFoundException.class, RunNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> missing(RuntimeException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(RunAlreadyStartedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(RunAlreadyStartedException exception) {
        return Map.of(
                "message", exception.getMessage(),
                "runId", exception.runId().toString());
    }

    public record StartRun(String storyId, String triggeredBy) {
    }
}
