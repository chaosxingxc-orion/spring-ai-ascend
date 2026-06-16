package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.spi.PayloadRefStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Local-filesystem {@link PayloadRefStore}: writes payload content to a configurable
 * directory as {@code <sha256>.json} and returns {@code payload_ref://<sha256>} as the opaque
 * reference. SHA-256 content-addressing ensures duplicate payloads share one file.
 *
 * <p>This is a reference implementation for development and single-node deployments.
 * Production multi-node deployments should supply a shared-storage implementation
 * (S3, GCS, MinIO) by registering a {@link PayloadRefStore} bean.
 */
final class LocalFsPayloadRefStore implements PayloadRefStore {

    static final String REF_SCHEME = "payload_ref://";

    private final Path directory;

    LocalFsPayloadRefStore(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create payload-ref directory: " + directory, e);
        }
    }

    @Override
    public String put(String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256Hex(bytes);
        Path target = directory.resolve(sha256 + ".json");
        if (!Files.exists(target)) {
            try {
                Files.write(target, bytes);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to write payload ref: " + target, e);
            }
        }
        return REF_SCHEME + sha256;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
