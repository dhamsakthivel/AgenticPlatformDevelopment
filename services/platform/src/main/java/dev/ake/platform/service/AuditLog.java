package dev.ake.platform.service;

import dev.ake.platform.domain.Models.AuditEntry;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AuditLog {

    private static final int LIMIT = 40;
    private final Deque<AuditEntry> entries = new ArrayDeque<>();

    public synchronized void record(String action, String subject, String detail) {
        entries.addFirst(new AuditEntry(Instant.now(), action, subject, detail));
        while (entries.size() > LIMIT) {
            entries.removeLast();
        }
    }

    public synchronized List<AuditEntry> latest() {
        return new ArrayList<>(entries);
    }
}
