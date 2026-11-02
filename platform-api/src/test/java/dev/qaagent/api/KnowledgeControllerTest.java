package dev.qaagent.api;

import dev.qaagent.core.IntegrationException;
import dev.qaagent.knowledge.ingest.KnowledgeService;
import dev.qaagent.knowledge.retrieve.RankedChunk;
import dev.qaagent.knowledge.store.ChunkStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KnowledgeControllerTest {

    @Mock
    private KnowledgeService knowledge;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new KnowledgeController(knowledge)).build();
    }

    @Test
    void searchReturnsHits() throws Exception {
        UUID chunkId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(knowledge.search(eq("spouse dependent"), isNull(), isNull(), eq("enrollment"), isNull(), isNull()))
                .thenReturn(List.of(new RankedChunk(chunkId, chunkId, "Spouse rule", "upload",
                        "example-benefit-plan", "enrollment", "A spouse may enroll.", 0.03)));

        mvc.perform(get("/api/knowledge/search").param("q", "spouse dependent").param("module", "enrollment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("spouse dependent"))
                .andExpect(jsonPath("$.hits[0].chunkId").value(chunkId.toString()))
                .andExpect(jsonPath("$.hits[0].text").value("A spouse may enroll."));
    }

    @Test
    void documentsReturnsTheIndexedNotes() throws Exception {
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(knowledge.documents()).thenReturn(List.of(new ChunkStore.ListedDocument(
                id, "Enrollment rules", "knowledge-seed", "enrollment-business-rules.md",
                "example-benefit-plan", "enrollment", 4, Instant.parse("2026-11-01T20:00:00Z"))));

        mvc.perform(get("/api/knowledge/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Enrollment rules"))
                .andExpect(jsonPath("$[0].chunks").value(4));
    }

    @Test
    void searchWithoutAQueryIsRejected() throws Exception {
        mvc.perform(get("/api/knowledge/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add a search query, for example q=spouse dependent."));
    }

    @Test
    void uploadSendsTheFileBytes() throws Exception {
        byte[] body = "Spouse dependent".getBytes(StandardCharsets.UTF_8);
        when(knowledge.ingestUpload(eq("rules.md"), argThat(bytes -> Arrays.equals(bytes, body)), eq("Rules"),
                eq("example-benefit-plan"), eq("enrollment"), eq("upload")))
                .thenReturn(new KnowledgeService.IngestedDocument(
                        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                        "Rules", "upload", "rules.md", 1, false));

        mvc.perform(multipart("/api/knowledge/documents")
                        .file(new MockMultipartFile("file", "rules.md", "text/markdown", body))
                        .param("title", "Rules")
                        .param("app", "example-benefit-plan")
                        .param("module", "enrollment")
                        .param("source", "upload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunks").value(1))
                .andExpect(jsonPath("$.skippedUnchanged").value(false));
    }

    @Test
    void emptyUploadIsRejected() throws Exception {
        mvc.perform(multipart("/api/knowledge/documents")
                        .file(new MockMultipartFile("file", "empty.md", MediaType.TEXT_PLAIN_VALUE, new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a Markdown or PDF file to upload."));
    }

    @Test
    void reindexReturnsTheReport() throws Exception {
        when(knowledge.reindex()).thenReturn(new KnowledgeService.ReindexReport(4, 0, 6, List.of()));

        mvc.perform(post("/api/knowledge/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingested").value(4))
                .andExpect(jsonPath("$.chunks").value(6));

        verify(knowledge).reindex();
    }

    @Test
    void integrationFailureIsABadRequest() throws Exception {
        when(knowledge.search(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IntegrationException("Knowledge seed directory was not found."));

        mvc.perform(get("/api/knowledge/search").param("q", "spouse"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Knowledge seed directory was not found."));
    }
}
