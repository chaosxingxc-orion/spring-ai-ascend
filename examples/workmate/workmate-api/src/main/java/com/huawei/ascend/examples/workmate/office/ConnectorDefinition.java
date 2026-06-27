package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import java.util.List;
import java.util.Optional;

public record ConnectorDefinition(
        String id,
        String name,
        String nameEn,
        String description,
        String type,
        String authMethod,
        String source,
        boolean defaultEnabled,
        List<String> examples,
        McpServerConfig mcpConfig,
        boolean runnable) {

    public ConnectorDefinition {
        if (examples == null) {
            examples = List.of();
        }
        if (type == null || type.isBlank()) {
            type = "mcp";
        }
        if (source == null || source.isBlank()) {
            source = "marketplace";
        } else {
            source = OfficeMarketText.normalizeSource(source);
        }
    }

    public Optional<McpServerConfig> resolvedMcpConfig() {
        return Optional.ofNullable(mcpConfig);
    }

    public boolean requiresAuth() {
        if (authMethod == null || authMethod.isBlank()) {
            return false;
        }
        return !"NONE".equalsIgnoreCase(authMethod);
    }
}
