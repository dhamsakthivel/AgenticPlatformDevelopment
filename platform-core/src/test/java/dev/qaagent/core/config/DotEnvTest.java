package dev.qaagent.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotEnvTest {

    @TempDir
    Path directory;

    @Test
    void parseKeepsQuotedValuesAndSkipsComments() {
        Map<String, String> values = DotEnv.parse("""
                # comment
                OPENAI_API_KEY="sk-test"
                export OPENAI_CHAT_MODEL='gpt-5-mini'
                EMPTY=
                not a pair
                """);

        assertThat(values).containsEntry("OPENAI_API_KEY", "sk-test");
        assertThat(values).containsEntry("OPENAI_CHAT_MODEL", "gpt-5-mini");
        assertThat(values).containsEntry("EMPTY", "");
        assertThat(values).doesNotContainKey("not a pair");
    }

    @Test
    void processValueWinsOverTheFileAndTheFileWinsOverConfiguration() {
        assertThat(DotEnv.pick("from-process", "from-file", "from-config")).isEqualTo("from-process");
        assertThat(DotEnv.pick("  ", "from-file", "from-config")).isEqualTo("from-file");
        assertThat(DotEnv.pick(null, null, " from-config ")).isEqualTo("from-config");
        assertThat(DotEnv.pick(null, null, null)).isEmpty();
    }

    @Test
    void locateFindsAFileInAParentDirectory() throws Exception {
        Path nested = Files.createDirectories(directory.resolve("frontend"));
        Files.writeString(directory.resolve(".env"), "OPENAI_API_KEY=sk-parent\n");

        assertThat(DotEnv.locate(nested)).isEqualTo(directory.resolve(".env"));
        assertThat(DotEnv.locate(directory.resolve("a/b/c/d/e"))).isNull();
    }

    @Test
    void aFileSavedAfterTheFirstReadIsUsedOnTheNextRead() throws Exception {
        Path root = Files.createDirectories(directory.resolve("repo"));
        assertThat(DotEnv.fileValue(root, "OPENAI_API_KEY")).isEmpty();

        Files.writeString(root.resolve(".env"), "OPENAI_API_KEY=sk-later\n");
        assertThat(DotEnv.fileValue(root, "OPENAI_API_KEY")).isEqualTo("sk-later");

        Files.writeString(root.resolve(".env"), "OPENAI_API_KEY=sk-updated\n");
        Files.setLastModifiedTime(root.resolve(".env"), FileTime.from(Instant.now().plusSeconds(2)));
        assertThat(DotEnv.fileValue(root, "OPENAI_API_KEY")).isEqualTo("sk-updated");
    }
}
