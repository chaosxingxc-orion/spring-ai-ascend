package com.huawei.ascend.examples.workmate.office;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class McpJsonConfigMapper {

    private final ObjectMapper objectMapper;

    public McpJsonConfigMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<McpServerConfig> parse(
            String connectorId, Path connectorDir, String mcpFile, boolean defaultEnabled) {
        String fileName = mcpFile == null || mcpFile.isBlank() ? "mcp.json" : mcpFile;
        Path path = connectorDir.resolve(fileName).normalize();
        if (!path.startsWith(connectorDir) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(path));
            JsonNode servers = root.get("mcpServers");
            if (servers == null || !servers.isObject() || servers.isEmpty()) {
                return Optional.empty();
            }
            JsonNode spec = servers.elements().next();
            return Optional.of(toServerConfig(connectorId, spec, root, defaultEnabled));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse MCP config for connector " + connectorId, ex);
        }
    }

    private McpServerConfig toServerConfig(
            String connectorId, JsonNode spec, JsonNode root, boolean defaultEnabled) {
        String url = text(spec, "url");
        String command = text(spec, "command");
        List<String> args = stringList(spec.get("args"));
        Map<String, String> env = mergeEnv(spec, root);
        Map<String, String> headers = stringMap(spec.get("headers"));
        long timeoutSeconds = timeoutSeconds(spec);
        boolean disabled = spec.path("disabled").asBoolean(false);
        String transport = resolveTransport(spec, url, command);
        String endpoint = text(spec, "endpoint");
        return new McpServerConfig(
                connectorId,
                defaultEnabled && !disabled,
                transport,
                command,
                args,
                url,
                endpoint,
                headers,
                List.of(),
                env,
                timeoutSeconds);
    }

    static String resolveTransport(JsonNode spec, String url, String command) {
        if (url != null && !url.isBlank()) {
            String raw = firstNonBlank(
                    text(spec, "type"),
                    text(spec, "transportType"),
                    text(spec, "transport"));
            return normalizeHttpTransport(raw);
        }
        if (command != null && !command.isBlank()) {
            return "stdio";
        }
        return "stdio";
    }

    private static String normalizeHttpTransport(String raw) {
        if (raw == null || raw.isBlank()) {
            return "streamable-http";
        }
        String normalized = raw.trim().toLowerCase().replace('_', '-');
        if ("streamablehttp".equals(normalized.replace("-", ""))) {
            return "streamable-http";
        }
        if ("sse".equals(normalized)) {
            return "sse";
        }
        if ("streamable-http".equals(normalized)) {
            return "streamable-http";
        }
        return "streamable-http";
    }

    private static Map<String, String> mergeEnv(JsonNode spec, JsonNode root) {
        Map<String, String> env = new LinkedHashMap<>();
        putMap(env, spec.get("env"));
        putMap(env, spec.get("staticEnv"));
        putMap(env, root.get("staticEnv"));
        return Map.copyOf(env);
    }

    private static void putMap(Map<String, String> target, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isNull()) {
                target.put(entry.getKey(), entry.getValue().asText(""));
            }
        });
    }

    private static long timeoutSeconds(JsonNode spec) {
        if (!spec.has("timeout")) {
            return 0L;
        }
        long raw = spec.get("timeout").asLong(0L);
        if (raw <= 0) {
            return 0L;
        }
        // Upstream manifests often use millisecond timeouts (e.g. 120000).
        return raw >= 1000L ? raw / 1000L : raw;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isNull()) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    private static Map<String, String> stringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText("")));
        return Map.copyOf(values);
    }
}
