package dev.qaagent.knowledge.load;

import dev.qaagent.core.IntegrationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextLoaderTest {

    private final DocumentTextLoader loader = new DocumentTextLoader();

    @Test
    void readsMarkdownAsUtf8() {
        assertThat(loader.read("rules.md", "Spouse dependent".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("Spouse dependent");
    }

    @Test
    void readsTextFromAPdf() throws Exception {
        byte[] pdf = pdf("Spouse dependent coverage");

        assertThat(loader.read("notes.pdf", pdf)).contains("Spouse dependent coverage");
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> loader.read("empty.md", new byte[0]))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("empty");
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
