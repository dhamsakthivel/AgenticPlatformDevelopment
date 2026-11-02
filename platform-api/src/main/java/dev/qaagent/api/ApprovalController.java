package dev.qaagent.api;

import dev.qaagent.orchestrator.run.ApprovalClosedException;
import dev.qaagent.orchestrator.run.ApprovalNotFoundException;
import dev.qaagent.orchestrator.run.ApprovalService;
import dev.qaagent.orchestrator.run.RunStore;
import dev.qaagent.orchestrator.story.ApprovalDecision;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping
    public List<RunStore.ApprovalView> list(@RequestParam(required = false) String status) {
        return approvals.list(status);
    }

    @PostMapping("/{id}/decision")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> decide(@PathVariable UUID id, @RequestBody ApprovalDecision decision) {
        approvals.decide(id, decision);
        return Map.of("status", "signalled", "approvalId", id.toString());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> rejected(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(ApprovalNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> missing(ApprovalNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(ApprovalClosedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> closed(ApprovalClosedException exception) {
        return Map.of("message", exception.getMessage());
    }
}
