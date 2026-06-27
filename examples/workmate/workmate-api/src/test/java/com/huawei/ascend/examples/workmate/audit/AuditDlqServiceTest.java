package com.huawei.ascend.examples.workmate.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditDlqServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void appendsJsonlEntry() throws Exception {
        AuditDlqService dlq = new AuditDlqService(
                new ObjectMapper(), new WorkmateDataProperties(tempDir.toString()));
        UUID sessionId = UUID.randomUUID();

        boolean ok = dlq.append(sessionId, "run-1", "tool.start", Map.of("toolName", "bash"), "db down");

        assertThat(ok).isTrue();
        assertThat(dlq.queuedEntries()).isEqualTo(1);
        Path file = tempDir.resolve("audit-dlq").resolve("audit-dlq.jsonl");
        assertThat(Files.exists(file)).isTrue();
        String line = Files.readString(file);
        assertThat(line).contains("tool.start").contains(sessionId.toString());
    }
}
