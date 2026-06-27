package com.huawei.ascend.examples.workmate.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoryStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void appendCreatesBackupAndReadsContent() throws Exception {
        MemoryStore store = new MemoryStore(new WorkmateDataProperties(tempDir.toString()));

        store.append("default", List.of("Prefers concise answers"));
        store.append("default", List.of("Role: fund manager"));

        String content = store.read("default");
        assertThat(content).contains("Prefers concise answers").contains("Role: fund manager");
        assertThat(Files.exists(store.backupFile("default"))).isTrue();
    }

    @Test
    void clearRemovesMemoryAndBackup() {
        MemoryStore store = new MemoryStore(new WorkmateDataProperties(tempDir.toString()));
        store.append("default", List.of("line"));
        assertThat(store.exists("default")).isTrue();

        store.clear("default");

        assertThat(store.read("default")).isBlank();
        assertThat(Files.exists(store.backupFile("default"))).isFalse();
    }
}
