package com.huawei.ascend.examples.workmate.mcp;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class McpToolAllowlistResolver {

    private McpToolAllowlistResolver() {
    }

    static List<String> resolve(String serverId, List<String> configured) {
        String envKey = "WORKMATE_MCP_" + serverId.toUpperCase(Locale.ROOT).replace('-', '_') + "_TOOL_ALLOWLIST";
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return Arrays.stream(envValue.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .collect(Collectors.toList());
        }
        return configured == null ? List.of() : configured;
    }
}
