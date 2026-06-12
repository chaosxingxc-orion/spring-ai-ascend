package com.huawei.ascend.agentsdk.example.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReadFileTool {
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    private ReadFileTool() {
    }

    public static Map<String, Object> read(Map<String, Object> inputs) throws IOException {
        INVOCATIONS.incrementAndGet();
        Object rawPath = inputs.get("path");
        if (rawPath == null || String.valueOf(rawPath).isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        Path path = Path.of(String.valueOf(rawPath)).toAbsolutePath().normalize();
        System.out.println("[agent-sdk-example] ReadFileTool.read invoked with path=" + path);
        return Map.of(
                "path", path.toString(),
                "content", Files.readString(path, StandardCharsets.UTF_8),
                "proof", "readFile-java-tool-executed");
    }

    public static int invocationCount() {
        return INVOCATIONS.get();
    }

    public static void reset() {
        INVOCATIONS.set(0);
    }
}
