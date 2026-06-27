package com.huawei.ascend.examples.workmate.office;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class StudioZipWriter {

    private StudioZipWriter() {}

    static byte[] buildZip(Map<String, byte[]> entries) {
        Map<String, byte[]> ordered = new LinkedHashMap<>(entries);
        try (var baos = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : ordered.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build export zip", ex);
        }
    }

    static void addDirectoryFiles(Path dir, String zipPrefix, Map<String, byte[]> target) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String relative = dir.relativize(path).toString().replace('\\', '/');
                    String zipPath = zipPrefix.endsWith("/") ? zipPrefix + relative : zipPrefix + "/" + relative;
                    target.put(zipPath, Files.readAllBytes(path));
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to read draft file " + path, ex);
                }
            });
        }
    }

    static byte[] readme(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
