package dev.ake.platform.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final DocumentService documents;

    public SeedRunner(DocumentService documents) {
        this.documents = documents;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!documents.list().isEmpty()) {
            return;
        }
        ClassPathResource resource = new ClassPathResource("seed/harbor-field-prd.md");
        String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var document = documents.seed("seed-harbor", "Harbor Field Service brief", text);
        log.info("Seeded Harbor brief ({}, {})", document.id(), document.ingestStatus());
    }
}
