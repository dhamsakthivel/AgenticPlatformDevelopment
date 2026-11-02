package dev.qaagent.api;

import dev.qaagent.orchestrator.run.RunStore;
import dev.qaagent.orchestrator.run.StepEvent;
import dev.qaagent.orchestrator.run.StepEvents;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class RunEventStream implements StepEvents {

    private final ConcurrentHashMap<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID runId, List<RunStore.StepView> existing) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(runId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(error -> remove(runId, emitter));
        for (RunStore.StepView step : existing) {
            send(emitter, new StepEvent(
                    runId.toString(),
                    step.id().toString(),
                    step.stepName(),
                    step.status(),
                    step.error()));
        }
        return emitter;
    }

    @Override
    public void publish(StepEvent event) {
        List<SseEmitter> subscribers = emitters.get(UUID.fromString(event.runId()));
        if (subscribers == null) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            send(emitter, event);
        }
    }

    private void send(SseEmitter emitter, StepEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.status()).data(event, MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            emitter.complete();
        }
    }

    private void remove(UUID runId, SseEmitter emitter) {
        List<SseEmitter> subscribers = emitters.get(runId);
        if (subscribers != null) {
            subscribers.remove(emitter);
        }
    }
}
