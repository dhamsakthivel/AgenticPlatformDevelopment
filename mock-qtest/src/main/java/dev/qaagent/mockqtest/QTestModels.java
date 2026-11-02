package dev.qaagent.mockqtest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request and response shapes follow qTest Manager API v3 field names
 * ({@code parent_id}, {@code test_steps}, {@code field_name}). List calls return a JSON array.
 */
final class QTestModels {

    private QTestModels() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Project(long id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Module(long id, String name, @JsonProperty("parent_id") Long parentId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Property(
            @JsonProperty("field_id") Long fieldId,
            @JsonProperty("field_name") String fieldName,
            @JsonProperty("field_value") String fieldValue
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Step(String description, String expected, Integer order) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestCaseWrite(
            String name,
            @JsonProperty("parent_id") Long parentId,
            String description,
            String precondition,
            List<Property> properties,
            @JsonProperty("test_steps") List<Step> testSteps
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestCase(
            long id,
            String pid,
            String name,
            @JsonProperty("parent_id") long parentId,
            String description,
            String precondition,
            @JsonProperty("web_url") String webUrl,
            List<Property> properties,
            @JsonProperty("test_steps") List<Step> testSteps,
            @JsonIgnore long projectId
    ) {
    }

    record Comment(long id, String content) {
    }

    record CommentWrite(String content) {
    }
}
