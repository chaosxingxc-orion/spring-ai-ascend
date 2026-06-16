package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** LocalFsPayloadRefStore: SHA-256 content-addressing, dedup, put-only invariant. */
class LocalFsPayloadRefStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void putReturnsPayloadRefSchemeWithSha256() {
        LocalFsPayloadRefStore store = new LocalFsPayloadRefStore(tempDir);
        String ref = store.put("hello world");
        assertThat(ref).startsWith(LocalFsPayloadRefStore.REF_SCHEME);
        // ref = payload_ref://<64-hex-chars-sha256>
        String sha = ref.substring(LocalFsPayloadRefStore.REF_SCHEME.length());
        assertThat(sha).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void payloadIsPersistedAndReadableFromDisk() throws IOException {
        LocalFsPayloadRefStore store = new LocalFsPayloadRefStore(tempDir);
        String payload = "sensitive large payload content";
        String ref = store.put(payload);
        String sha = ref.substring(LocalFsPayloadRefStore.REF_SCHEME.length());

        Path file = tempDir.resolve(sha + ".json");
        assertThat(file).exists();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void sameSha256DeduplicatesWithoutRewrite() throws IOException {
        LocalFsPayloadRefStore store = new LocalFsPayloadRefStore(tempDir);
        String payload = "dedup test";
        String ref1 = store.put(payload);
        long mtime = Files.getLastModifiedTime(
                tempDir.resolve(ref1.substring(LocalFsPayloadRefStore.REF_SCHEME.length()) + ".json")).toMillis();

        // Put the same content a second time — file must NOT be rewritten
        String ref2 = store.put(payload);
        long mtime2 = Files.getLastModifiedTime(
                tempDir.resolve(ref2.substring(LocalFsPayloadRefStore.REF_SCHEME.length()) + ".json")).toMillis();

        assertThat(ref1).isEqualTo(ref2);
        assertThat(mtime2).isEqualTo(mtime);
    }

    @Test
    void differentPayloadsYieldDifferentRefs() {
        LocalFsPayloadRefStore store = new LocalFsPayloadRefStore(tempDir);
        String ref1 = store.put("payload-A");
        String ref2 = store.put("payload-B");
        assertThat(ref1).isNotEqualTo(ref2);
    }

    @Test
    void nonExistentParentDirectoryIsCreatedAutomatically() throws IOException {
        Path nested = tempDir.resolve("a").resolve("b").resolve("c");
        assertThat(nested).doesNotExist();
        LocalFsPayloadRefStore store = new LocalFsPayloadRefStore(nested);
        store.put("x");
        assertThat(nested).isDirectory();
    }
}
