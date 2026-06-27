package com.huawei.ascend.examples.workmate.connector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Canonical connector id resolution. Expert skillCompatibility and legacy configs may use
 * aliases (e.g. {@code qieman-mcp}) that differ from gateway MCP server ids ({@code qieman}).
 */
public final class ConnectorIds {

    private static final Map<String, String> ALIASES = Map.of(
            "qieman-mcp", "qieman");

    private ConnectorIds() {}

    public static String normalize(String connectorId) {
        if (connectorId == null) {
            return null;
        }
        String trimmed = connectorId.strip();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String key = trimmed.toLowerCase(Locale.ROOT);
        String aliased = ALIASES.get(key);
        if (aliased != null) {
            return aliased;
        }
        if (trimmed.endsWith("-mcp")) {
            return trimmed.substring(0, trimmed.length() - 4);
        }
        return trimmed;
    }

    public static List<String> normalize(Collection<String> connectorIds) {
        if (connectorIds == null || connectorIds.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String connectorId : connectorIds) {
            String canonical = normalize(connectorId);
            if (canonical != null && !canonical.isBlank()) {
                normalized.add(canonical);
            }
        }
        return List.copyOf(new ArrayList<>(normalized));
    }
}
