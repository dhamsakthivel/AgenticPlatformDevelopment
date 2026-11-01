package dev.qaagent.integrations.ado;

import dev.qaagent.integrations.workitem.SeedStory;
import dev.qaagent.integrations.workitem.StoryCatalog;

import java.util.List;

/**
 * Creates the synthetic enrollment stories in a sandbox Azure DevOps project.
 * Requires ADO_ORG_URL, ADO_PROJECT, and ADO_PAT. A story whose title already exists is left in place.
 */
public final class SeedAdoStories {

    public static void main(String[] args) {
        String orgUrl = required("ADO_ORG_URL");
        String project = required("ADO_PROJECT");
        String pat = required("ADO_PAT");
        String path = System.getenv().getOrDefault("STORIES_SEED_PATH", "scripts/seed/stories.json");
        List<SeedStory> stories = StoryCatalog.load(StoryCatalog.resolve(path));
        AdoWorkItemClient client = new AdoWorkItemClient(orgUrl, project, pat);
        for (SeedStory story : stories) {
            String id = client.ensureUserStory(story);
            System.out.println(story.id() + " -> work item " + id + " (" + story.title() + ")");
        }
        System.out.println("Seeded " + stories.size() + " stories in " + project + ".");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + name + " before seeding Azure DevOps.");
        }
        return value;
    }
}
