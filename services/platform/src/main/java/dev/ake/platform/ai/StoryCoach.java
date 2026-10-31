package dev.ake.platform.ai;

import dev.ake.platform.domain.Models.CoachNote;
import dev.ake.platform.domain.Models.UserStory;
import org.springframework.ai.chat.client.ChatClient;

public class StoryCoach {

    private final ChatClient client;

    public StoryCoach(ChatClient client) {
        this.client = client;
    }

    public CoachNote sharpen(UserStory story) {
        if (client == null) {
            return new CoachNote(
                    false,
                    "Spring AI coach is idle. Set OPENAI_API_KEY to ask a model to tighten this story.");
        }
        String criteria = String.join("\n- ", story.acceptanceCriteria());
        String suggestion = client.prompt()
                .system("""
                        You are a strict story editor. Tighten one user story.
                        Keep it grounded in the story text. Do not invent product scope.
                        Reply in under 120 words with a sharper benefit line and any missing test.
                        """)
                .user(story.description() + "\nAcceptance criteria:\n- " + criteria)
                .call()
                .content();
        return new CoachNote(true, suggestion == null ? "" : suggestion.trim());
    }
}
