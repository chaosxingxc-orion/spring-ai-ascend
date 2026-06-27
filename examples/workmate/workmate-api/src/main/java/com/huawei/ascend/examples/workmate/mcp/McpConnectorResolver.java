package com.huawei.ascend.examples.workmate.mcp;

import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties;
import com.huawei.ascend.examples.workmate.connector.ConnectorAuthMethod;
import com.huawei.ascend.examples.workmate.connector.ConnectorCatalog;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import com.huawei.ascend.examples.workmate.office.ConnectorDefinition;
import com.huawei.ascend.examples.workmate.office.ConnectorRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class McpConnectorResolver {

    private final WorkmateMcpProperties mcpProperties;
    private final ConnectorRegistry connectorRegistry;

    public McpConnectorResolver(WorkmateMcpProperties mcpProperties, ConnectorRegistry connectorRegistry) {
        this.mcpProperties = mcpProperties;
        this.connectorRegistry = connectorRegistry;
    }

    public Optional<McpServerConfig> findConfig(String connectorId) {
        Optional<McpServerConfig> yaml = mcpProperties.servers().stream()
                .filter(server -> server.id().equals(connectorId))
                .findFirst();
        if (yaml.isPresent()) {
            return yaml;
        }
        return connectorRegistry.findConnector(connectorId).flatMap(ConnectorDefinition::resolvedMcpConfig);
    }

    public McpServerConfig requireConfig(String connectorId) {
        return findConfig(connectorId).orElseThrow(() -> new McpServerNotFoundException(connectorId));
    }

    public List<ConnectorDefinition> catalogConnectors() {
        Map<String, ConnectorDefinition> merged = new LinkedHashMap<>();
        connectorRegistry.listConnectors().forEach(connector -> merged.put(connector.id(), connector));
        for (McpServerConfig server : mcpProperties.servers()) {
            merged.putIfAbsent(server.id(), fromYamlServer(server));
        }
        return List.copyOf(merged.values());
    }

    private ConnectorDefinition fromYamlServer(McpServerConfig server) {
        var meta = ConnectorCatalog.find(server.id());
        String authMethod = meta.map(ConnectorCatalog.ConnectorMeta::authMethod)
                .map(ConnectorAuthMethod::name)
                .orElse(server.headers().containsKey("x-api-key") ? "API_KEY" : "NONE");
        return new ConnectorDefinition(
                server.id(),
                meta.map(ConnectorCatalog.ConnectorMeta::name).orElse(server.id()),
                null,
                meta.map(ConnectorCatalog.ConnectorMeta::description).orElse(server.id()),
                "mcp",
                authMethod,
                "dogfood",
                server.enabled(),
                List.of(),
                server,
                true);
    }
}
