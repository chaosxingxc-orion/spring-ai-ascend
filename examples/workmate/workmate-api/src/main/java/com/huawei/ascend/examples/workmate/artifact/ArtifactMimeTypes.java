package com.huawei.ascend.examples.workmate.artifact;

import java.util.Locale;
import java.util.Map;

public final class ArtifactMimeTypes {

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry(".md", "text/markdown"),
            Map.entry(".markdown", "text/markdown"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".html", "text/html"),
            Map.entry(".htm", "text/html"),
            Map.entry(".json", "application/json"),
            Map.entry(".xml", "application/xml"),
            Map.entry(".csv", "text/csv"),
            Map.entry(".js", "text/javascript"),
            Map.entry(".ts", "text/typescript"),
            Map.entry(".css", "text/css"),
            Map.entry(".yaml", "text/yaml"),
            Map.entry(".yml", "text/yaml"),
            Map.entry(".py", "text/x-python"),
            Map.entry(".java", "text/x-java"),
            Map.entry(".sh", "text/x-shellscript"),
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".woff", "font/woff"),
            Map.entry(".woff2", "font/woff2"));

    private ArtifactMimeTypes() {
    }

    public static String guess(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "application/octet-stream";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "application/octet-stream";
        }
        String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
        return BY_EXTENSION.getOrDefault(ext, "application/octet-stream");
    }
}
