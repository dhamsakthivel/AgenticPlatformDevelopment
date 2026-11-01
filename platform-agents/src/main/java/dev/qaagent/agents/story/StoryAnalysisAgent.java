package dev.qaagent.agents.story;

import dev.qaagent.agents.prompt.PromptRegistry;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.llm.LlmRequest;
import dev.qaagent.core.llm.LlmResult;
import dev.qaagent.core.port.LlmClient;
import dev.qaagent.core.port.WorkItemPort;
import dev.qaagent.core.schema.SchemaFiles;
import dev.qaagent.core.story.RetrievedChunk;
import dev.qaagent.core.story.Story;
import dev.qaagent.core.story.StoryAnalysisInput;
import dev.qaagent.core.story.StoryAnalysisOutput;
import dev.qaagent.knowledge.ingest.KnowledgeService;
import dev.qaagent.knowledge.retrieve.RankedChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StoryAnalysisAgent {

    static final String AGENT = "story-analysis";
    static final String SCHEMA_ID = "story-analysis-output";

    private final PromptRegistry prompts;
    private final LlmClient llm;
    private final WorkItemPort stories;
    private final KnowledgeService knowledge;

    public StoryAnalysisAgent(PromptRegistry prompts, LlmClient llm, WorkItemPort stories, KnowledgeService knowledge) {
        this.prompts = prompts;
        this.llm = llm;
        this.stories = stories;
        this.knowledge = knowledge;
    }

    public StoryAnalysisOutput analyze(String storyId) {
        Story story = stories.getStory(storyId);
        List<RankedChunk> hits = knowledge.search(
                story.title() + "\n" + story.description(),
                null, null, null, AGENT, null);
        List<RetrievedChunk> context = new ArrayList<>();
        for (RankedChunk hit : hits) {
            context.add(new RetrievedChunk(hit.chunkId().toString(), hit.source(), hit.title(), hit.text()));
        }
        return analyze(new StoryAnalysisInput(
                story.id(), story.title(), story.description(), story.acceptanceCriteria(), context));
    }

    public StoryAnalysisOutput analyze(StoryAnalysisInput input) {
        String prompt = prompts.render(AGENT, Map.of(
                "storyId", blank(input.storyId()),
                "title", blank(input.title()),
                "description", blank(input.description()),
                "acceptanceCriteria", criteria(input.acceptanceCriteria()),
                "context", context(input.retrievedContext()),
                "schema", SchemaFiles.text(SCHEMA_ID)));
        LlmResult<StoryAnalysisOutput> result = llm.structured(request(prompt), StoryAnalysisOutput.class, SCHEMA_ID);
        String citationError = citationError(input, result.value());
        if (citationError == null) {
            return result.value();
        }
        result = llm.structured(request(prompt + "\n\n" + citationError), StoryAnalysisOutput.class, SCHEMA_ID);
        citationError = citationError(input, result.value());
        if (citationError != null) {
            throw new IntegrationException("The analysis cited chunk ids that were not in the retrieved notes.");
        }
        return result.value();
    }

    private LlmRequest request(String content) {
        return new LlmRequest(AGENT, AGENT, prompts.version(AGENT), content, SchemaFiles.text(SCHEMA_ID));
    }

    static String citationError(StoryAnalysisInput input, StoryAnalysisOutput output) {
        Set<String> allowed = new LinkedHashSet<>();
        for (RetrievedChunk chunk : input.retrievedContext()) {
            if (chunk.chunkId() != null && !chunk.chunkId().isBlank()) {
                allowed.add(chunk.chunkId());
            }
        }
        List<String> invented = new ArrayList<>();
        for (StoryAnalysisOutput.Citation citation : output.citations()) {
            if (citation.chunkId() == null || !allowed.contains(citation.chunkId())) {
                invented.add(citation.chunkId() == null ? "(blank)" : citation.chunkId());
            }
        }
        if (invented.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        text.append("The previous answer cited chunk ids that were not in the retrieved excerpts: ");
        text.append(String.join(", ", invented)).append(".\n");
        if (allowed.isEmpty()) {
            text.append("No excerpts were provided. Return an empty citations array.");
        } else {
            text.append("Cite only these chunk ids: ").append(String.join(", ", allowed)).append(".");
        }
        return text.toString();
    }

    private static String criteria(List<String> criteria) {
        if (criteria.isEmpty()) {
            return "(none listed)";
        }
        StringBuilder text = new StringBuilder();
        for (String criterion : criteria) {
            text.append("- ").append(criterion).append('\n');
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

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
