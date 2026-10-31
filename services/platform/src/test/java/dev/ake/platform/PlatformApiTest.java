package dev.ake.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"ake.agent.mode=stub", "spring.profiles.active=local"})
class PlatformApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void rejectsMissingApiKey() throws Exception {
        mvc.perform(get("/api/stories")).andExpect(status().isUnauthorized());
    }

    @Test
    void seedsLibraryAndGeneratesAStory() throws Exception {
        mvc.perform(get("/api/documents").header("X-API-Key", "dev-ake-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Harbor Field Service brief"))
                .andExpect(jsonPath("$[0].ingestStatus").value("indexed"));

        MvcResult created = mvc.perform(post("/api/stories/generate")
                        .header("X-API-Key", "dev-ake-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Notify the technician when the window changes\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").exists())
                .andExpect(jsonPath("$.points").value(5))
                .andReturn();

        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(patch("/api/stories/" + id)
                        .header("X-API-Key", "dev-ake-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ready\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"));

        mvc.perform(get("/api/insights").header("X-API-Key", "dev-ake-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stories").value(1));

        mvc.perform(get("/api/system").header("X-API-Key", "dev-ake-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.apiKeyRequired").value(true))
                .andExpect(jsonPath("$.agent.llm").value("stub"));
    }
}
