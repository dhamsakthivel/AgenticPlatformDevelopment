package dev.qaagent.knowledge.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qaagent.core.IntegrationException;
import dev.qaagent.core.StoryNotFoundException;
import dev.qaagent.core.phi.PhiRedactor;
import dev.qaagent.core.port.EmbeddingPort;
import dev.qaagent.core.port.TestManagementPort;
import dev.qaagent.core.port.WorkItemPort;
import dev.qaagent.core.story.Story;
import dev.qaagent.core.testcase.ExternalTestCase;
import dev.qaagent.knowledge.chunk.ChunkTexts;
import dev.qaagent.knowledge.chunk.MarkdownChunker;
import dev.qaagent.knowledge.config.KnowledgeProperties;
import dev.qaagent.knowledge.config.RetrievalProperties;
import dev.qaagent.knowledge.load.ContentHasher;
import dev.qaagent.knowledge.load.DocumentTextLoader;
import dev.qaagent.knowledge.retrieve.HybridRetriever;
import dev.qaagent.knowledge.retrieve.RankedChunk;
import dev.qaagent.knowledge.store.ChunkStore;
import dev.qaagent.knowledge.store.SearchFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final String APP = "example-benefit-plan";
    private static final String MODULE = "enrollment";

    private final PhiRedactor redactor;
    private final MarkdownChunker chunker;
    private final DocumentTextLoader loader;
    private final EmbeddingPort embeddings;
    private final ChunkStore store;
    private final HybridRetriever retriever;
    private final KnowledgeProperties knowledge;
    private final RetrievalProperties retrieval;
    private final WorkItemPort stories;
    private final TestManagementPort testCases;
    private final ObjectMapper json;

    public KnowledgeService(PhiRedactor redactor, EmbeddingPort embeddings, ChunkStore store,
                            HybridRetriever retriever, KnowledgeProperties knowledge, RetrievalProperties retrieval,
                            WorkItemPort stories, TestManagementPort testCases, ObjectMapper json) {
        this.redactor = redactor;
        this.chunker = new MarkdownChunker();
        this.loader = new DocumentTextLoader();
        this.embeddings = embeddings;
        this.store = store;
        this.retriever = retriever;
        this.knowledge = knowledge;
        this.retrieval = retrieval;
        this.stories = stories;
        this.testCases = testCases;
        this.json = json;
    }

    @Transactional
    public IngestedDocument ingestUpload(String filename, byte[] bytes, String title, String app, String module, String source) {
        String text = loader.read(filename, bytes);
        String resolvedSource = blank(source) ? "upload" : source;
        return ingest(new IngestDraft(
                resolvedSource,
                filename == null ? "upload" : filename,
                blank(title) ? filename : title,
                blank(app) ? APP : app,
                blank(module) ? MODULE : module,
                "upload",
                text,
                Kind.MARKDOWN));
    }

    @Transactional
    public ReindexReport reindex() {
        int ingested = 0;
        int skipped = 0;
        int chunks = 0;
        List<String> warnings = new ArrayList<>();
        for (Path file : seedFiles()) {
            IngestedDocument document = ingest(new IngestDraft(
                    "knowledge-seed",
                    file.getFileName().toString(),
                    titleFrom(file),
                    APP,
                    MODULE,
                    "1",
                    read(file),
                    Kind.MARKDOWN));
            if (document.skippedUnchanged()) {
                skipped++;
            } else {
                ingested++;
                chunks += document.chunks();
            }
        }
        for (String storyId : storyIds(warnings)) {
            try {
                Story story = stories.getStory(storyId);
                IngestedDocument document = ingest(new IngestDraft(
                        "story",
                        story.id(),
                        story.title(),
                        APP,
                        blank(story.module()) ? MODULE : story.module(),
                        story.state(),
                        ChunkTexts.story(story),
                        Kind.SINGLE));
                if (document.skippedUnchanged()) {
                    skipped++;
                } else {
                    ingested++;
                    chunks += document.chunks();
                }
            } catch (StoryNotFoundException exception) {
                warnings.add("Story " + storyId + " was not found.");
            }
        }
        try {
            for (ExternalTestCase testCase : testCases.findByModule(MODULE)) {
                IngestedDocument document = ingest(new IngestDraft(
                        "test-case",
                        testCase.ref().testCaseId(),
                        testCase.draft().title(),
                        APP,
                        blank(testCase.module()) ? MODULE : testCase.module(),
                        testCase.status().name(),
                        ChunkTexts.testCase(testCase),
                        Kind.SINGLE));
                if (document.skippedUnchanged()) {
                    skipped++;
                } else {
                    ingested++;
                    chunks += document.chunks();
                }
            }
        } catch (IntegrationException exception) {
            warnings.add("Test cases were not indexed: " + exception.getMessage());
        }
        log.info("knowledge reindex ingested={} skipped={} chunks={} warnings={}", ingested, skipped, chunks, warnings.size());
        return new ReindexReport(ingested, skipped, chunks, List.copyOf(warnings));
    }

    public List<RankedChunk> search(String query, String source, String app, String module, String profile, Integer limit) {
        RetrievalProperties.Profile configured = retrieval.profile(profile);
        List<String> sources = source == null || source.isBlank()
                ? (configured == null ? List.of() : configured.sources())
                : List.of(source);
        String resolvedApp = blank(app) ? (configured == null ? null : configured.app()) : app;
        String resolvedModule = blank(module) ? (configured == null ? null : configured.module()) : module;
        int resolvedLimit = limit != null && limit > 0 ? limit : (configured == null ? 8 : configured.limit());
        return retriever.search(query, new SearchFilter(sources, resolvedApp, resolvedModule), resolvedLimit);
    }

    public List<ChunkStore.ListedDocument> documents() {
        return store.listDocuments();
    }

    private IngestedDocument ingest(IngestDraft draft) {
        String redacted = redactor.redact(draft.text());
        String hash = ContentHasher.sha256(redacted);
        Optional<ChunkStore.StoredDocument> existing = store.find(draft.source(), draft.uri());
        if (existing.isPresent() && hash.equals(existing.get().contentHash())) {
            return new IngestedDocument(existing.get().id(), draft.title(), draft.source(), draft.uri(), 0, true);
        }
        List<String> chunks = draft.kind() == Kind.MARKDOWN ? chunker.chunk(redacted) : List.of(redacted.trim());
        chunks = chunks.stream().filter(chunk -> !chunk.isBlank()).toList();
        if (chunks.isEmpty()) {
            throw new IntegrationException("Document " + draft.uri() + " has no text after redaction.");
        }
        List<float[]> vectors = embeddings.embed(chunks);
        UUID id;
        if (existing.isPresent()) {
            store.replace(existing.get(), draft.source(), draft.title(), draft.app(), draft.module(), draft.version(),
                    hash, chunks, vectors, embeddings.modelName());
            id = existing.get().id();
        } else {
            id = store.insert(draft.source(), draft.uri(), draft.title(), draft.app(), draft.module(), draft.version(),
                    hash, chunks, vectors, embeddings.modelName());
        }
        return new IngestedDocument(id, draft.title(), draft.source(), draft.uri(), chunks.size(), false);
    }

    private List<Path> seedFiles() {
        Path directory = resolve(knowledge.seedPath());
        if (!Files.isDirectory(directory)) {
            throw new IntegrationException("Knowledge seed directory was not found at " + directory.toAbsolutePath());
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IntegrationException("Could not list " + directory, exception);
        }
    }

    private List<String> storyIds(List<String> warnings) {
        Path path = resolve(knowledge.storiesPath());
        if (!Files.isRegularFile(path)) {
            warnings.add("Stories file was not found at " + path.toAbsolutePath());
            return List.of();
        }
        try {
            List<String> ids = new ArrayList<>();
            for (JsonNode story : json.readTree(path.toFile())) {
                String id = story.path("id").asText("");
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
            return ids;
        } catch (IOException exception) {
            warnings.add("Could not read story ids from " + path.toAbsolutePath());
            return List.of();
        }
    }

    private static Path resolve(String configured) {
        Path direct = Path.of(configured == null ? "" : configured);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromParent = Path.of("..").resolve(configured == null ? "" : configured).normalize();
        if (Files.exists(fromParent)) {
            return fromParent;
        }
        return direct;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new IntegrationException("Could not read " + file, exception);
        }
    }

    private static String titleFrom(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".md") ? name.substring(0, name.length() - 3).replace('-', ' ') : name;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private enum Kind {
        MARKDOWN,
        SINGLE
    }

    private record IngestDraft(String source, String uri, String title, String app, String module, String version,
                               String text, Kind kind) {
    }

    public record IngestedDocument(UUID documentId, String title, String source, String uri, int chunks,
                                   boolean skippedUnchanged) {
    }

    public record ReindexReport(int ingested, int skippedUnchanged, int chunks, List<String> warnings) {
    }
}
