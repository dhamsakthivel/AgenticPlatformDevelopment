package dev.ake.platform.service;

import dev.ake.platform.domain.Models.Insights;
import dev.ake.platform.domain.Models.Insights.RiskCount;
import dev.ake.platform.domain.Models.UserStory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InsightService {

    private final StoryService stories;

    public InsightService(StoryService stories) {
        this.stories = stories;
    }

    public Insights summarize() {
        List<UserStory> all = stories.list();
        if (all.isEmpty()) {
            return new Insights(
                    0,
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("Generate a few stories before the forecast has anything to say."));
        }
        double averagePoints = all.stream().mapToInt(UserStory::points).average().orElse(0);
        double averageConfidence = all.stream().mapToDouble(UserStory::confidence).average().orElse(0);
        Map<String, Integer> risks = new LinkedHashMap<>();
        for (UserStory story : all) {
            for (String risk : story.risks()) {
                risks.merge(risk, 1, Integer::sum);
            }
        }
        List<RiskCount> riskCounts = risks.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> new RiskCount(entry.getKey(), entry.getValue()))
                .toList();
        List<UserStory> ready = all.stream()
                .filter(story -> story.confidence() >= 0.5 && story.points() <= 8)
                .sorted(Comparator.comparingInt(UserStory::points))
                .limit(5)
                .toList();
        List<UserStory> watch = all.stream()
                .filter(story -> story.confidence() < 0.5 || story.risks().size() >= 2)
                .limit(5)
                .toList();
        List<String> notes = new ArrayList<>();
        notes.add(String.format(
                "Backlog averages %.1f points with %.0f%% mean confidence.",
                averagePoints, averageConfidence * 100));
        int load = ready.stream().mapToInt(UserStory::points).sum();
        notes.add("A cautious next slice is the " + ready.size() + " higher-confidence stories at " + load + " points.");
        if (!riskCounts.isEmpty()) {
            notes.add("Most common risk signal: " + riskCounts.getFirst().risk() + ".");
        }
        return new Insights(all.size(), round(averagePoints), round(averageConfidence), riskCounts, ready, watch, notes);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
