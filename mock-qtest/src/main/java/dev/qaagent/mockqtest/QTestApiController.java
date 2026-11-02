package dev.qaagent.mockqtest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

import static dev.qaagent.mockqtest.QTestModels.Comment;
import static dev.qaagent.mockqtest.QTestModels.CommentWrite;
import static dev.qaagent.mockqtest.QTestModels.Module;
import static dev.qaagent.mockqtest.QTestModels.Project;
import static dev.qaagent.mockqtest.QTestModels.TestCase;
import static dev.qaagent.mockqtest.QTestModels.TestCaseWrite;

/**
 * qTest Manager API v3 subset. No token is required.
 * Test-case lists are a JSON array, which is what {@code QTestClient} reads.
 * {@code parentId} may be a module id or a module name.
 */
@RestController
@RequestMapping("/api/v3/projects")
class QTestApiController {

    private final QTestStore store;

    QTestApiController(QTestStore store) {
        this.store = store;
    }

    @GetMapping
    List<Project> projects() {
        return store.projects();
    }

    @GetMapping("/{projectId}/modules")
    List<Module> modules(@PathVariable long projectId) {
        return store.modules(projectId);
    }

    @PostMapping("/{projectId}/modules")
    ResponseEntity<Module> createModule(@PathVariable long projectId, @RequestBody Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        return ResponseEntity.status(HttpStatus.CREATED).body(store.createModule(projectId, name));
    }

    @PostMapping("/{projectId}/test-cases")
    ResponseEntity<TestCase> createCase(@PathVariable long projectId,
                                         @RequestBody TestCaseWrite body,
                                         HttpServletRequest request) {
        TestCase created = store.createCase(projectId, body, "");
        TestCase withUrl = store.setWebUrl(projectId, created.id(), webUrl(request, projectId, created.id()));
        return ResponseEntity.status(HttpStatus.CREATED).body(withUrl);
    }

    @PutMapping("/{projectId}/test-cases/{testCaseId}")
    TestCase updateCase(@PathVariable long projectId,
                         @PathVariable long testCaseId,
                         @RequestBody TestCaseWrite body) {
        return store.updateCase(projectId, testCaseId, body);
    }

    @GetMapping("/{projectId}/test-cases/{testCaseId}")
    TestCase getCase(@PathVariable long projectId, @PathVariable long testCaseId) {
        return store.getCase(projectId, testCaseId);
    }

    @GetMapping("/{projectId}/test-cases")
    List<TestCase> listCases(@PathVariable long projectId,
                             @RequestParam(required = false) String parentId,
                             @RequestParam(required = false) Integer size) {
        return store.listCases(projectId, parentId, size);
    }

    @PostMapping("/{projectId}/test-cases/{testCaseId}/comments")
    ResponseEntity<Comment> addComment(@PathVariable long projectId,
                                        @PathVariable long testCaseId,
                                        @RequestBody CommentWrite body) {
        String content = body == null ? "" : body.content();
        return ResponseEntity.status(HttpStatus.CREATED).body(store.addComment(projectId, testCaseId, content));
    }

    private static String webUrl(HttpServletRequest request, long projectId, long testCaseId) {
        return ServletUriComponentsBuilder.fromRequest(request)
                .replacePath("/api/v3/projects/" + projectId + "/test-cases/" + testCaseId)
                .replaceQuery(null)
                .build()
                .toUriString();
    }
}
