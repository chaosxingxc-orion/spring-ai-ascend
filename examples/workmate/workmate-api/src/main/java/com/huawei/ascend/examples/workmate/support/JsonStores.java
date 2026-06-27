package com.huawei.ascend.examples.workmate.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Helpers for the small JSON "stores" under {@code data/} (share links, favorites, install state,
 * etc.). These are plain files rewritten in full on every change; a naive {@code writeValue(file)}
 * truncates the target before writing, so a concurrent reader (or a crash mid-write) can observe a
 * truncated/corrupt file and lose data. {@link #writeAtomic} writes to a sibling temp file and then
 * atomically renames it into place.
 */
public final class JsonStores {

    private JsonStores() {
    }

    public static void writeAtomic(ObjectMapper objectMapper, Path target, Object value) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), value);
            try {
                Files.move(tmp, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
