package com.huawei.ascend.examples.workmate.connector;

import com.huawei.ascend.examples.workmate.capability.CapabilityUsageService;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import com.huawei.ascend.examples.workmate.connector.dto.ConnectorAuthProfileResponse;
import com.huawei.ascend.examples.workmate.connector.dto.ConnectorResponse;
import com.huawei.ascend.examples.workmate.connector.dto.ConnectConnectorRequest;
import com.huawei.ascend.examples.workmate.mcp.McpConnectionService;
import com.huawei.ascend.examples.workmate.mcp.McpConnectorResolver;
import com.huawei.ascend.examples.workmate.mcp.McpGateway;
import com.huawei.ascend.examples.workmate.office.ConnectorDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConnectorService {

    private final WorkmateMcpProperties mcpProperties;
    private final McpGateway gateway;
    private final ConnectorCredentialStore credentialStore;
    private final McpConnectionService connectionService;
    private final OAuthService oauthService;
    private final CapabilityUsageService capabilityUsageService;
    private final McpConnectorResolver connectorResolver;

    public ConnectorService(
            WorkmateMcpProperties mcpProperties,
            McpGateway gateway,
            ConnectorCredentialStore credentialStore,
            McpConnectionService connectionService,
            OAuthService oauthService,
            CapabilityUsageService capabilityUsageService,
            McpConnectorResolver connectorResolver) {
        this.mcpProperties = mcpProperties;
        this.gateway = gateway;
        this.credentialStore = credentialStore;
        this.connectionService = connectionService;
        this.oauthService = oauthService;
        this.capabilityUsageService = capabilityUsageService;
        this.connectorResolver = connectorResolver;
    }

    public List<ConnectorResponse> listConnectors() {
        return connectorResolver.catalogConnectors().stream().map(this::toResponse).toList();
    }

    public ConnectorResponse getConnector(String connectorId) {
        ConnectorDefinition definition = connectorResolver.catalogConnectors().stream()
                .filter(connector -> connector.id().equals(connectorId))
                .findFirst()
                .orElseThrow(() -> new ConnectorNotFoundException(connectorId));
        return toResponse(definition);
    }

    public ConnectorResponse connect(String connectorId, ConnectConnectorRequest request) {
        ensureRunnable(connectorId);
        McpServerConfig base = requireConfig(connectorId);
        Map<String, String> headers = mergeHeaders(base, request);
        if (requiresApiKey(connectorId) && !hasApiKey(headers)) {
            throw new IllegalArgumentException("API key required for connector: " + connectorId);
        }
        credentialStore.save(connectorId, headers);
        connectionService.reconnect(base, headers);
        capabilityUsageService.recordUsage("connector", connectorId);
        return getConnector(connectorId);
    }

    public ConnectorResponse disconnect(String connectorId) {
        findDefinition(connectorId);
        connectionService.disconnect(connectorId);
        credentialStore.clear(connectorId);
        return toResponse(findDefinition(connectorId));
    }

    public ConnectorResponse reconnect(String connectorId) {
        ensureRunnable(connectorId);
        McpServerConfig base = requireConfig(connectorId);
        Map<String, String> headers = mergeHeaders(base, new ConnectConnectorRequest(null, null));
        if (requiresApiKey(connectorId) && !hasApiKey(headers)) {
            throw new IllegalArgumentException("API key required for connector: " + connectorId);
        }
        McpGateway.McpServerSummary summary = connectionService.reconnect(base, headers);
        return toResponse(findDefinition(connectorId), summary);
    }

    public ConnectorResponse revoke(String connectorId) {
        findDefinition(connectorId);
        connectionService.disconnect(connectorId);
        oauthService.revoke(connectorId);
        return toResponse(findDefinition(connectorId));
    }

    public ConnectorAuthProfileResponse authProfile(String connectorId) {
        ConnectorDefinition definition = findDefinition(connectorId);
        ConnectorAuthMethod method = ConnectorAuthMethod.fromDefinition(definition);
        return new ConnectorAuthProfileResponse(
                connectorId,
                method.name(),
                oauthService.hasCredential(connectorId),
                oauthService.credentialMask(connectorId).orElse(null));
    }

    public ConnectorResponse connectWithOAuthHeaders(String connectorId, Map<String, String> headers) {
        ensureRunnable(connectorId);
        McpServerConfig base = requireConfig(connectorId);
        if (requiresApiKey(connectorId) && !hasApiKey(headers)) {
            throw new IllegalArgumentException("API key required for connector: " + connectorId);
        }
        credentialStore.save(connectorId, headers);
        connectionService.reconnect(base, headers);
        capabilityUsageService.recordUsage("connector", connectorId);
        return getConnector(connectorId);
    }

    public boolean requiresAuth(String connectorId) {
        ConnectorDefinition definition = findDefinition(connectorId);
        ConnectorAuthMethod method = ConnectorAuthMethod.fromDefinition(definition);
        return method != ConnectorAuthMethod.NONE && method != ConnectorAuthMethod.CLI_LOGIN;
    }

    private ConnectorDefinition findDefinition(String connectorId) {
        return connectorResolver.catalogConnectors().stream()
                .filter(connector -> connector.id().equals(connectorId))
                .findFirst()
                .orElseThrow(() -> new ConnectorNotFoundException(connectorId));
    }

    private McpServerConfig requireConfig(String connectorId) {
        return connectorResolver.requireConfig(connectorId);
    }

    private void ensureRunnable(String connectorId) {
        if (!mcpProperties.enabled()) {
            throw new IllegalStateException("MCP gateway disabled (workmate.mcp.enabled=false)");
        }
        ConnectorDefinition definition = findDefinition(connectorId);
        if (!definition.runnable()) {
            throw new IllegalStateException("Connector is catalog-only (no MCP transport): " + connectorId);
        }
    }

    private ConnectorResponse toResponse(ConnectorDefinition definition) {
        McpGateway.McpServerSummary summary = gateway.serverSummary(definition.id());
        return toResponse(definition, summary);
    }

    private ConnectorResponse toResponse(ConnectorDefinition definition, McpGateway.McpServerSummary summary) {
        String connectorId = definition.id();
        boolean gatewayEnabled = mcpProperties.enabled();
        boolean connected = gatewayEnabled && summary.connected();
        String status = resolveStatus(definition, gatewayEnabled, connected, summary.lastError());

        ConnectorAuthMethod authMethod = ConnectorAuthMethod.fromDefinition(definition);
        boolean requiresAuth = authMethod != ConnectorAuthMethod.NONE;
        String authHint = ConnectorCatalog.find(connectorId)
                .map(ConnectorCatalog.ConnectorMeta::authHint)
                .orElse(null);
        String name = firstNonBlank(
                ConnectorCatalog.find(connectorId).map(ConnectorCatalog.ConnectorMeta::name).orElse(null),
                definition.name(),
                connectorId);
        String description = firstNonBlank(
                ConnectorCatalog.find(connectorId)
                        .map(ConnectorCatalog.ConnectorMeta::description)
                        .orElse(null),
                definition.description(),
                connectorId);

        return new ConnectorResponse(
                connectorId,
                name,
                description,
                status,
                gatewayEnabled ? summary.toolCount() : 0,
                requiresAuth,
                authHint,
                authMethod.name(),
                oauthService.hasCredential(connectorId),
                oauthService.credentialMask(connectorId).orElse(null),
                gatewayEnabled ? summary.invalidSchemaCount() : 0,
                gatewayEnabled && summary.toolsLimitWarning(),
                gatewayEnabled ? summary.lastError() : unavailableMessage(definition, gatewayEnabled),
                definition.runnable() && gatewayEnabled,
                definition.source());
    }

    private static String unavailableMessage(ConnectorDefinition definition, boolean gatewayEnabled) {
        if (!gatewayEnabled) {
            return "MCP gateway disabled";
        }
        if (!definition.runnable()) {
            return "Catalog-only connector (CLI / pending MCP)";
        }
        return null;
    }

    private static String resolveStatus(
            ConnectorDefinition definition, boolean gatewayEnabled, boolean connected, String lastError) {
        if (!gatewayEnabled || !definition.runnable()) {
            return "disconnected";
        }
        if (connected) {
            return "connected";
        }
        if (lastError != null && !lastError.isBlank()) {
            return "error";
        }
        return "disconnected";
    }

    private Map<String, String> mergeHeaders(McpServerConfig base, ConnectConnectorRequest request) {
        Map<String, String> headers = new HashMap<>(base.headers());
        credentialStore.find(base.id()).ifPresent(headers::putAll);
        headers.putAll(request.headers());
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            headers.put("x-api-key", request.apiKey().trim());
        }
        return headers;
    }

    private boolean requiresApiKey(String connectorId) {
        ConnectorAuthMethod method = ConnectorAuthMethod.fromDefinition(findDefinition(connectorId));
        return method == ConnectorAuthMethod.API_KEY || method == ConnectorAuthMethod.DEVICE_CODE;
    }

    private static boolean hasApiKey(Map<String, String> headers) {
        String value = headers.get("x-api-key");
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
