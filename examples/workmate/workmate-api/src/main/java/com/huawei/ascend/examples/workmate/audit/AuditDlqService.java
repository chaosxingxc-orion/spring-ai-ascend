package com.huawei.ascend.examples.workmate.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dead-letter queue for audit events that could not be persisted to run_events.
 * Local JSONL under {@code data/audit-dlq/} (W22 / B1).
 */
@Service
public class AuditDlqService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditDlqService.class);

    private final ObjectMapper objectMapper;
    private final Path dlqDirectory;
    private final AtomicLong queuedEntries = new AtomicLong(0);
    private volatile Instant lastWriteAt;
    private volatile String lastWriteFailure;

    public AuditDlqService(ObjectMapper objectMapper, WorkmateDataProperties dataProperties) {
        this.objectMapper = objectMapper;
        this.dlqDirectory = Path.of(dataProperties.path(), "audit-dlq");
    }

    public boolean append(UUID sessionId, String runId, String eventName, Map<String, Object> payload, String reason) {
        Map<String, Object> entry = Map.of(
                "ts", Instant.now().toString(),
                "sessionId", sessionId.toString(),
                "runId", runId,
                "eventName", eventName,
                "reason", reason,
                "payload", payload);
        try {
            Files.createDirectories(dlqDirectory);
            Path file = dlqDirectory.resolve("audit-dlq.jsonl");
            String line = objectMapper.writeValueAsString(entry) + "\n";
            try (BufferedWriter writer = Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
            }
            queuedEntries.incrementAndGet();
            lastWriteAt = Instant.now();
            lastWriteFailure = null;
            return true;
        } catch (IOException ex) {
            lastWriteFailure = ex.getMessage();
            LOG.warn("Failed to append audit DLQ entry sessionId={} event={}: {}", sessionId, eventName, ex.getMessage());
            return false;
        }
    }

    public long queuedEntries() {
        return queuedEntries.get();
    }

    public Instant lastWriteAt() {
        return lastWriteAt;
    }

    public String lastWriteFailure() {
        return lastWriteFailure;
    }

    public Path dlqDirectory() {
        return dlqDirectory;
    }
}
