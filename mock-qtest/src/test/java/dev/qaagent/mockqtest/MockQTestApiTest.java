package dev.qaagent.mockqtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MockQTestApiTest {

    private static final String CASE = """
            {
              "name": "Spouse is stored",
              "parent_id": 1,
              "description": "Key: TC1",
              "precondition": "Subscriber is active",
              "test_steps": [
                {"description": "Submit ADD", "expected": "ACCEPTED", "order": 1}
              ],
              "properties": [
                {"field_name": "Status", "field_value": "DRAFT"}
              ]
            }
            """;

    private final MockMvc mockMvc;

    @Autowired
    MockQTestApiTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void projectsModulesAndTestCasesFollowTheClientContract() throws Exception {
        mockMvc.perform(get("/api/v3/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Enrollment"));

        mockMvc.perform(post("/api/v3/projects/1/test-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CASE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.pid").value("TC-1"))
                .andExpect(jsonPath("$.parent_id").value(1))
                .andExpect(jsonPath("$.web_url").value(org.hamcrest.Matchers.containsString("/api/v3/projects/1/test-cases/1")))
                .andExpect(jsonPath("$.properties[0].field_value").value("DRAFT"))
                .andExpect(jsonPath("$.test_steps[0].expected").value("ACCEPTED"));

        mockMvc.perform(get("/api/v3/projects/1/test-cases/1").param("expandSteps", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spouse is stored"))
                .andExpect(jsonPath("$.precondition").value("Subscriber is active"));

        mockMvc.perform(get("/api/v3/projects/1/test-cases")
                        .param("parentId", "enrollment")
                        .param("expandSteps", "true")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        mockMvc.perform(get("/api/v3/projects/1/test-cases").param("parentId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pid").value("TC-1"));

        mockMvc.perform(put("/api/v3/projects/1/test-cases/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CASE.replace("DRAFT", "APPROVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.properties[0].field_value").value("APPROVED"))
                .andExpect(jsonPath("$.test_steps[0].description").value("Submit ADD"))
                .andExpect(jsonPath("$.web_url").value(org.hamcrest.Matchers.containsString("/test-cases/1")));

        mockMvc.perform(post("/api/v3/projects/1/test-cases/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Needs a human review\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Needs a human review"));

        mockMvc.perform(post("/api/v3/projects/1/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"claims\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("claims"));

        mockMvc.perform(get("/api/v3/projects/99/test-cases/1"))
                .andExpect(status().isNotFound());
    }
}
