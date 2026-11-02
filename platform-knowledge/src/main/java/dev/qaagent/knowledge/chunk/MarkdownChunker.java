package dev.qaagent.knowledge.chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits Markdown on headings, then packs the pieces into roughly 300–800 tokens.
 * One token is estimated as four characters.
 */
public final class MarkdownChunker {

    public static final int MIN_TOKENS = 300;
    public static final int MAX_TOKENS = 800;

    public List<String> chunk(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<String> pieces = new ArrayList<>();
        for (String section : splitHeadings(markdown)) {
            pieces.addAll(splitOversized(section.trim()));
        }
        return pack(pieces);
    }

    static int tokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private static List<String> splitHeadings(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("#") && current.length() > 0) {
                sections.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        if (current.length() > 0) {
            sections.add(current.toString());
        }
        return sections;
    }

    private static List<String> splitOversized(String section) {
        if (tokens(section) <= MAX_TOKENS) {
            return section.isBlank() ? List.of() : List.of(section);
        }
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : section.split("\\n\\s*\\n")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (tokens(trimmed) > MAX_TOKENS) {
                if (current.length() > 0) {
                    pieces.add(current.toString());
                    current.setLength(0);
                }
                pieces.addAll(splitByLength(trimmed));
                continue;
            }
            if (current.length() > 0 && tokens(current + "\n\n" + trimmed) > MAX_TOKENS) {
                pieces.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private static List<String> splitByLength(String text) {
        int maxChars = MAX_TOKENS * 4;
        List<String> pieces = new ArrayList<>();
        for (int start = 0; start < text.length(); start += maxChars) {
            pieces.add(text.substring(start, Math.min(text.length(), start + maxChars)));
        }
        return pieces;
    }

    private static List<String> pack(List<String> pieces) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isBlank()) {
                continue;
            }
            String combined = current.isEmpty() ? piece : current + "\n\n" + piece;
            if (!current.isEmpty() && tokens(combined) > MAX_TOKENS) {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(piece);
                continue;
            }
            current.setLength(0);
            current.append(combined);
            if (tokens(current.toString()) >= MIN_TOKENS) {
                chunks.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.isEmpty()) {
            return List.copyOf(chunks);
        }
        if (!chunks.isEmpty()) {
            String last = chunks.get(chunks.size() - 1);
            String merged = last + "\n\n" + current;
            if (tokens(merged) <= MAX_TOKENS) {
                chunks.set(chunks.size() - 1, merged);
                return List.copyOf(chunks);
            }
        }
        chunks.add(current.toString());
        return List.copyOf(chunks);
    }
}
