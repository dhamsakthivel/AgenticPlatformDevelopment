package dev.qaagent.api;

import dev.qaagent.core.IntegrationException;
import dev.qaagent.knowledge.ingest.KnowledgeService;
import dev.qaagent.knowledge.retrieve.RankedChunk;
import dev.qaagent.knowledge.store.ChunkStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeService.IngestedDocument upload(@RequestPart("file") MultipartFile file,
                                                    @RequestParam(required = false) String title,
                                                    @RequestParam(required = false) String app,
                                                    @RequestParam(required = false) String module,
                                                    @RequestParam(required = false) String source) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IntegrationException("Choose a Markdown or PDF file to upload.");
        }
        return knowledge.ingestUpload(file.getOriginalFilename(), file.getBytes(), title, app, module, source);
    }

    @GetMapping("/documents")
    public List<ChunkStore.ListedDocument> documents() {
        return knowledge.documents();
    }

    @PostMapping("/reindex")
    public KnowledgeService.ReindexReport reindex() {
        return knowledge.reindex();
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) String source,
                                 @RequestParam(required = false) String app,
                                 @RequestParam(required = false) String module,
                                 @RequestParam(required = false) String profile,
                                 @RequestParam(required = false) Integer limit) {
        if (q == null || q.isBlank()) {
            throw new IntegrationException("Add a search query, for example q=spouse dependent.");
        }
        return new SearchResponse(q, knowledge.search(q, source, app, module, profile, limit));
    }

    @ExceptionHandler(IntegrationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> rejected(IntegrationException exception) {
        return Map.of("message", exception.getMessage() == null ? "The knowledge request failed." : exception.getMessage());
    }

    public record SearchResponse(String query, List<RankedChunk> hits) {
    }
}
