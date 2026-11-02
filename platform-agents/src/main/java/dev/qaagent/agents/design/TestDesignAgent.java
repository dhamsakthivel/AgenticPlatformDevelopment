package dev.qaagent.agents.design;

import dev.qaagent.agents.prompt.PromptRegistry;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.design.TestDesignInput;
import dev.qaagent.core.design.TestDesignOutput;
import dev.qaagent.core.llm.LlmRequest;
import dev.qaagent.core.llm.LlmResult;
import dev.qaagent.core.port.LlmClient;
import dev.qaagent.core.schema.SchemaFiles;
import dev.qaagent.core.story.RetrievedChunk;
import dev.qaagent.core.story.StoryAnalysisOutput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TestDesignAgent {

    static final String AGENT = "test-design";
    static final String SCHEMA_ID = "test-design-output";

    private final PromptRegistry prompts;
    private final LlmClient llm;

    public TestDesignAgent(PromptRegistry prompts, LlmClient llm) {
        this.prompts = prompts;
        this.llm = llm;
    }

    public TestDesignOutput design(TestDesignInput input) {
        String prompt = prompts.render(AGENT, Map.of(
                "storyId", blank(input.storyId()),
                "title", blank(input.title()),
                "description", blank(input.description()),
                "acceptanceCriteria", lines(input.acceptanceCriteria()),
                "analysis", analysis(input.analysis()),
                "context", context(input.retrievedContext()),
                "similarCases", similar(input.similarCases()),
                "schema", SchemaFiles.text(SCHEMA_ID)));
        LlmResult<TestDesignOutput> result = llm.structured(request(prompt), TestDesignOutput.class, SCHEMA_ID);
        TestDesignOutput output = finish(input, result.value());
        String linkError = linkError(input, output);
        if (linkError == null) {
            return output;
        }
        result = llm.structured(request(prompt + "\n\n" + linkError), TestDesignOutput.class, SCHEMA_ID);
        output = finish(input, result.value());
        linkError = linkError(input, output);
        if (linkError != null) {
            throw new IntegrationException("The test design linked criteria that are not in the story analysis.");
        }
        return output;
    }

    private LlmRequest request(String content) {
        return new LlmRequest(AGENT, AGENT, prompts.version(AGENT), content, SchemaFiles.text(SCHEMA_ID));
    }

    private static TestDesignOutput finish(TestDesignInput input, TestDesignOutput output) {
        List<TestDesignOutput.DesignedCase> cases = new ArrayList<>();
        for (TestDesignOutput.DesignedCase item : output.testCases()) {
            String duplicate = known(item.possibleDuplicateOf(), input.similarCases());
            if (duplicate == null) {
                duplicate = TestDesignDuplicates.of(item.title(), input.similarCases());
            }
            cases.add(new TestDesignOutput.DesignedCase(
                    item.key(), item.title(), item.type(), item.priority(),
                    item.preconditions(), item.steps(), item.dataRequirements(),
                    item.linkedCriteria(), duplicate));
        }
        String storyId = output.storyId() == null || output.storyId().isBlank() ? input.storyId() : output.storyId();
        return new TestDesignOutput(storyId, cases);
    }

    static String linkError(TestDesignInput input, TestDesignOutput output) {
        Set<String> allowed = new LinkedHashSet<>();
        if (input.analysis() != null) {
            for (StoryAnalysisOutput.Criterion criterion : input.analysis().criteria()) {
                if (criterion.id() != null && !criterion.id().isBlank()) {
                    allowed.add(criterion.id());
                }
            }
        }
        List<String> invented = new ArrayList<>();
        for (TestDesignOutput.DesignedCase item : output.testCases()) {
            for (String link : item.linkedCriteria()) {
                if (!allowed.contains(link)) {
                    invented.add(link);
                }
            }
        }
        if (invented.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        text.append("The previous answer linked criterion ids that are not in the story analysis: ");
        text.append(String.join(", ", invented)).append(".\n");
        if (allowed.isEmpty()) {
            text.append("The analysis has no criteria. Use an empty linkedCriteria array on every case.");
        } else {
            text.append("Link only these ids: ").append(String.join(", ", allowed)).append(".");
        }
        return text.toString();
    }

    private static String known(String candidate, List<TestDesignInput.SimilarCase> existing) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        for (TestDesignInput.SimilarCase item : existing) {
            if (candidate.equals(item.id())) {
                return candidate;
            }
        }
        return null;
    }

    private static String analysis(StoryAnalysisOutput output) {
        if (output == null) {
            return "(no analysis)";
        }
        StringBuilder text = new StringBuilder();
        text.append("summary: ").append(blank(output.summary())).append('\n');
        text.append("riskScore: ").append(output.riskScore()).append('\n');
        text.append("criteria:\n");
        if (output.criteria().isEmpty()) {
            text.append("(none)\n");
        }
        for (StoryAnalysisOutput.Criterion criterion : output.criteria()) {
            text.append("- ").append(blank(criterion.id()))
                    .append(criterion.testable() ? " testable: " : " not testable: ")
                    .append(blank(criterion.text())).append('\n');
        }
        text.append("ambiguities:\n");
        if (output.ambiguities().isEmpty()) {
            text.append("(none)\n");
        }
        for (StoryAnalysisOutput.Ambiguity ambiguity : output.ambiguities()) {
            text.append("- ").append(ambiguity.severity()).append(": ").append(blank(ambiguity.question())).append('\n');
        }
        return text.toString().trim();
    }

    private static String context(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "(no retrieved excerpts)";
        }
        StringBuilder text = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            text.append("--- chunk ").append(chunk.chunkId())
                    .append(" source=").append(blank(chunk.source()))
                    .append(" title=").append(blank(chunk.title()))
                    .append(" ---\n");
            text.append(blank(chunk.text())).append("\n--- end chunk ---\n\n");
        }
        return text.toString().trim();
    }

    private static String similar(List<TestDesignInput.SimilarCase> cases) {
        if (cases.isEmpty()) {
            return "(no existing test cases)";
        }
        StringBuilder text = new StringBuilder();
        for (TestDesignInput.SimilarCase item : cases) {
            text.append("- id=").append(blank(item.id()))
                    .append(" title=").append(blank(item.title()))
                    .append("\n  ").append(blank(item.summary())).append('\n');
        }
        return text.toString().trim();
    }

    private static String lines(List<String> items) {
        if (items.isEmpty()) {
            return "(none listed)";
        }
        StringBuilder text = new StringBuilder();
        for (String item : items) {
            text.append("- ").append(item).append('\n');
        }
        return text.toString().trim();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
