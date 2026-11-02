package dev.qaagent.agents.design;

import dev.qaagent.core.design.TestDesignInput;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class TestDesignDuplicates {

    static final double THRESHOLD = 0.5;

    private TestDesignDuplicates() {
    }

    static String of(String title, List<TestDesignInput.SimilarCase> existing) {
        String bestId = null;
        double best = 0;
        for (TestDesignInput.SimilarCase candidate : existing) {
            double score = similarity(title, candidate.title());
            if (score > best) {
                best = score;
                bestId = candidate.id();
            }
        }
        if (best < THRESHOLD || bestId == null || bestId.isBlank()) {
            return null;
        }
        return bestId;
    }

    static double similarity(String left, String right) {
        Set<String> first = words(left);
        Set<String> second = words(right);
        if (first.isEmpty() || second.isEmpty()) {
            return 0;
        }
        int shared = 0;
        for (String word : first) {
            if (second.contains(word)) {
                shared++;
            }
        }
        return shared / (double) Math.min(first.size(), second.size());
    }

    private static Set<String> words(String text) {
        Set<String> words = new LinkedHashSet<>();
        if (text == null) {
            return words;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() > 2) {
                words.add(token);
            }
        }
        return words;
    }
}
