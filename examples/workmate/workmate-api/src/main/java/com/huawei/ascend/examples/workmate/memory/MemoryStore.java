package com.huawei.ascend.examples.workmate.memory;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MemoryStore {

    private static final String MEMORY_DIR = "memory";
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

    private final Path memoryRoot;

    public MemoryStore(WorkmateDataProperties dataProperties) {
        this.memoryRoot = dataProperties.resolvedPath().resolve(MEMORY_DIR);
    }

    public String read(String ownerId) {
        Path file = memoryFile(ownerId);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read memory for " + ownerId, ex);
        }
    }

    public void append(String ownerId, List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(memoryRoot);
            Path file = memoryFile(ownerId);
            if (Files.isRegularFile(file)) {
                Files.copy(file, backupFile(ownerId), StandardCopyOption.REPLACE_EXISTING);
            }
            List<String> lines = new ArrayList<>();
            if (Files.isRegularFile(file)) {
                lines.add(Files.readString(file, StandardCharsets.UTF_8).trim());
            }
            String stamp = TS.format(Instant.now());
            for (String entry : entries) {
                String line = entry == null ? "" : entry.trim();
                if (!line.isBlank()) {
                    lines.add("- [" + stamp + "] " + line);
                }
            }
            String body = String.join(System.lineSeparator(), lines).trim();
            if (!body.isBlank() && !body.endsWith(System.lineSeparator())) {
                body = body + System.lineSeparator();
            }
            Files.writeString(file, body, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to append memory for " + ownerId, ex);
        }
    }

    public void clear(String ownerId) {
        try {
            Files.deleteIfExists(memoryFile(ownerId));
            Files.deleteIfExists(backupFile(ownerId));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to clear memory for " + ownerId, ex);
        }
    }

    public boolean exists(String ownerId) {
        return Files.isRegularFile(memoryFile(ownerId));
    }

    Path memoryFile(String ownerId) {
        return memoryRoot.resolve(safeOwnerId(ownerId) + "_memory.md");
    }

    Path backupFile(String ownerId) {
        return memoryRoot.resolve(safeOwnerId(ownerId) + "_memory.md.bak");
    }

    private static String safeOwnerId(String ownerId) {
        return ownerId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
