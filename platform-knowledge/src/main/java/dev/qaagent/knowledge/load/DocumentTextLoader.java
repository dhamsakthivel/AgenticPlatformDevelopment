package dev.qaagent.knowledge.load;

import dev.qaagent.core.IntegrationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class DocumentTextLoader {

    public String read(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IntegrationException("The uploaded file is empty.");
        }
        String name = filename == null ? "" : filename.toLowerCase();
        if (name.endsWith(".pdf")) {
            return pdf(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public String pdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new IntegrationException("Could not read the PDF.", exception);
        }
    }
}
