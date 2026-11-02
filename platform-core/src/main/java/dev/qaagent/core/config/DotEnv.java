package dev.qaagent.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads {@code .env} from the working directory, or a parent of it, at the moment a value is needed.
 * A variable already set in the process environment wins.
 */
public final class DotEnv {

    private static volatile Snapshot snapshot = Snapshot.NONE;

    private DotEnv() {
    }

    public static String get(String key) {
        return resolve(key, "");
    }

    /**
     * Value for one model call. The process environment wins, then {@code .env}, then the startup value.
     */
    public static String resolve(String key, String configured) {
        return pick(System.getenv(key), fileValue(userDir(), key), configured);
    }

    public static String pick(String processValue, String fileValue, String configured) {
        if (present(processValue)) {
            return processValue.trim();
        }
        if (present(fileValue)) {
            return fileValue.trim();
        }
        return configured == null ? "" : configured.trim();
    }

    public static Map<String, String> fileValues() {
        return snapshot(userDir()).values;
    }

    static String fileValue(Path start, String key) {
        String value = snapshot(start).values.get(key);
        return value == null ? "" : value;
    }

    static Path locate(Path start) {
        Path dir = start == null ? null : start.toAbsolutePath().normalize();
        for (int hop = 0; dir != null && hop < 4; hop++) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    static Map<String, String> parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return values;
        }
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int split = line.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = line.substring(0, split).trim();
            String value = unquote(line.substring(split + 1).trim());
            if (!key.isEmpty()) {
                values.put(key, value);
            }
        }
        return values;
    }

    private static Path userDir() {
        return Path.of(System.getProperty("user.dir", "."));
    }

    private static Snapshot snapshot(Path start) {
        Path file = locate(start);
        if (file == null) {
            return Snapshot.NONE;
        }
        long modified = modified(file);
        Snapshot current = snapshot;
        if (file.equals(current.path) && modified == current.modified) {
            return current;
        }
        try {
            Snapshot next = new Snapshot(file, modified, Map.copyOf(parse(Files.readString(file))));
            snapshot = next;
            return next;
        } catch (IOException exception) {
            return current.path != null && file.equals(current.path) ? current : Snapshot.NONE;
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            return -1;
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private record Snapshot(Path path, long modified, Map<String, String> values) {
        private static final Snapshot NONE = new Snapshot(null, -1, Collections.emptyMap());
    }
}
