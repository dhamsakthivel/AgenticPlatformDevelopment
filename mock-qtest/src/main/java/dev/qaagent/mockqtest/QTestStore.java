package dev.qaagent.mockqtest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import static dev.qaagent.mockqtest.QTestModels.Comment;
import static dev.qaagent.mockqtest.QTestModels.Module;
import static dev.qaagent.mockqtest.QTestModels.Project;
import static dev.qaagent.mockqtest.QTestModels.Property;
import static dev.qaagent.mockqtest.QTestModels.Step;
import static dev.qaagent.mockqtest.QTestModels.TestCase;
import static dev.qaagent.mockqtest.QTestModels.TestCaseWrite;

@Component
class QTestStore {

    static final long ENROLLMENT_PROJECT_ID = 1L;
    static final long ENROLLMENT_MODULE_ID = 1L;

    private final ConcurrentMap<Long, Project> projects = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Module> modules = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, TestCase> cases = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, List<Comment>> comments = new ConcurrentHashMap<>();
    private final AtomicLong nextModuleId = new AtomicLong(ENROLLMENT_MODULE_ID + 1);
    private final AtomicLong nextCaseId = new AtomicLong(1);
    private final AtomicLong nextCommentId = new AtomicLong(1);

    QTestStore() {
        projects.put(ENROLLMENT_PROJECT_ID, new Project(ENROLLMENT_PROJECT_ID, "Enrollment"));
        modules.put(ENROLLMENT_MODULE_ID, new Module(ENROLLMENT_MODULE_ID, "enrollment", null));
    }

    List<Project> projects() {
        return projects.values().stream().sorted(Comparator.comparingLong(Project::id)).toList();
    }

    List<Module> modules(long projectId) {
        requireProject(projectId);
        return modules.values().stream().sorted(Comparator.comparingLong(Module::id)).toList();
    }

    Module createModule(long projectId, String name) {
        requireProject(projectId);
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Module name is required.");
        }
        long id = nextModuleId.getAndIncrement();
        Module module = new Module(id, name.trim(), null);
        modules.put(id, module);
        return module;
    }

    TestCase createCase(long projectId, TestCaseWrite body, String webUrl) {
        requireProject(projectId);
        long parentId = resolveParentId(body == null ? null : body.parentId());
        long id = nextCaseId.getAndIncrement();
        TestCase created = toCase(id, projectId, parentId, webUrl, body);
        cases.put(id, created);
        return created;
    }

    TestCase setWebUrl(long projectId, long testCaseId, String webUrl) {
        TestCase current = requireCase(projectId, testCaseId);
        TestCase updated = new TestCase(
                current.id(),
                current.pid(),
                current.name(),
                current.parentId(),
                current.description(),
                current.precondition(),
                webUrl,
                current.properties(),
                current.testSteps(),
                current.projectId());
        cases.put(testCaseId, updated);
        return updated;
    }

    TestCase updateCase(long projectId, long testCaseId, TestCaseWrite body) {
        TestCase current = requireCase(projectId, testCaseId);
        long parentId = body != null && body.parentId() != null
                ? resolveParentId(body.parentId())
                : current.parentId();
        TestCase updated = toCase(current.id(), projectId, parentId, current.webUrl(), body);
        cases.put(testCaseId, updated);
        return updated;
    }

    TestCase getCase(long projectId, long testCaseId) {
        return requireCase(projectId, testCaseId);
    }

    List<TestCase> listCases(long projectId, String parentId, Integer size) {
        requireProject(projectId);
        Long moduleId = resolveListParent(parentId);
        int limit = size == null || size < 1 ? 200 : size;
        return cases.values().stream()
                .filter(testCase -> testCase.projectId() == projectId)
                .filter(testCase -> moduleId == null || testCase.parentId() == moduleId)
                .sorted(Comparator.comparingLong(TestCase::id))
                .limit(limit)
                .toList();
    }

    Comment addComment(long projectId, long testCaseId, String content) {
        requireCase(projectId, testCaseId);
        Comment comment = new Comment(nextCommentId.getAndIncrement(), content == null ? "" : content);
        comments.computeIfAbsent(testCaseId, ignored -> Collections.synchronizedList(new ArrayList<>())).add(comment);
        return comment;
    }

    private TestCase toCase(long id, long projectId, long parentId, String webUrl, TestCaseWrite body) {
        String name = body == null || body.name() == null ? "" : body.name();
        String description = body == null || body.description() == null ? "" : body.description();
        String precondition = body == null || body.precondition() == null ? "" : body.precondition();
        List<Step> steps = body == null || body.testSteps() == null ? List.of() : List.copyOf(body.testSteps());
        return new TestCase(
                id,
                "TC-" + id,
                name,
                parentId,
                description,
                precondition,
                webUrl,
                propertiesOrDraft(body == null ? null : body.properties()),
                steps,
                projectId);
    }

    private List<Property> propertiesOrDraft(List<Property> properties) {
        if (properties == null || properties.isEmpty()) {
            return List.of(new Property(null, "Status", "DRAFT"));
        }
        boolean hasStatus = properties.stream().anyMatch(property -> "Status".equals(property.fieldName()));
        if (hasStatus) {
            return List.copyOf(properties);
        }
        List<Property> copy = new ArrayList<>(properties);
        copy.add(new Property(null, "Status", "DRAFT"));
        return List.copyOf(copy);
    }

    private long resolveParentId(Long parentId) {
        if (parentId == null) {
            return ENROLLMENT_MODULE_ID;
        }
        if (!modules.containsKey(parentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Module " + parentId + " was not found.");
        }
        return parentId;
    }

    private Long resolveListParent(String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return null;
        }
        if (parentId.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(parentId);
        }
        return modules.values().stream()
                .filter(module -> module.name().equalsIgnoreCase(parentId))
                .map(Module::id)
                .findFirst()
                .orElse(-1L);
    }

    private Project requireProject(long projectId) {
        Project project = projects.get(projectId);
        if (project == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project " + projectId + " was not found.");
        }
        return project;
    }

    private TestCase requireCase(long projectId, long testCaseId) {
        requireProject(projectId);
        TestCase testCase = cases.get(testCaseId);
        if (testCase == null || testCase.projectId() != projectId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test case " + testCaseId + " was not found.");
        }
        return testCase;
    }
}
