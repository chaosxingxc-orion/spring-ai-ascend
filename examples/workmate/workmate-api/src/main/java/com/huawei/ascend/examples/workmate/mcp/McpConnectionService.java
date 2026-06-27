package com.huawei.ascend.examples.workmate.mcp;

import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import com.huawei.ascend.examples.workmate.connector.ConnectorCredentialStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class McpConnectionService {

    private static final Logger LOG = LoggerFactory.getLogger(McpConnectionService.class);

    private final WorkmateMcpProperties properties;
    private final McpGateway gateway;
    private final ConnectorCredentialStore credentialStore;
    private final McpConnectorResolver connectorResolver;

    public McpConnectionService(
            WorkmateMcpProperties properties,
            McpGateway gateway,
            ConnectorCredentialStore credentialStore,
            McpConnectorResolver connectorResolver) {
        this.properties = properties;
        this.gateway = gateway;
        this.credentialStore = credentialStore;
        this.connectorResolver = connectorResolver;
    }

    public McpGateway.McpServerSummary reconnect(McpServerConfig base, Map<String, String> headers) {
        return reconnectWithBackoff(base, headers);
    }

    public McpGateway.McpServerSummary reconnectById(String serverId) {
        McpServerConfig base = findConfig(serverId)
                .orElseThrow(() -> new McpServerNotFoundException(serverId));
        Map<String, String> headers = new HashMap<>(base.headers());
        credentialStore.find(serverId).ifPresent(headers::putAll);
        return reconnectWithBackoff(base, headers);
    }

    public void disconnect(String serverId) {
        gateway.unregister(serverId);
        gateway.clearConnectionError(serverId);
    }

    public McpGateway.McpServerSummary disable(String serverId) {
        disconnect(serverId);
        return gateway.serverSummary(serverId);
    }

    private McpGateway.McpServerSummary reconnectWithBackoff(McpServerConfig base, Map<String, String> headers) {
        gateway.clearConnectionError(base.id());
        RuntimeException lastFailure = null;
        long backoffMs = properties.reconnectInitialBackoffMs();
        for (int attempt = 1; attempt <= properties.reconnectMaxAttempts(); attempt++) {
            try {
                if (attempt > 1) {
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                }
                doReconnect(base, headers);
                LOG.info("MCP reconnect succeeded server={} attempt={}", base.id(), attempt);
                return gateway.serverSummary(base.id());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                gateway.recordConnectionError(base.id(), "Reconnect interrupted");
                return gateway.serverSummary(base.id());
            } catch (RuntimeException ex) {
                lastFailure = ex;
                LOG.warn(
                        "MCP reconnect failed server={} attempt={}/{}: {}",
                        base.id(),
                        attempt,
                        properties.reconnectMaxAttempts(),
                        ex.getMessage());
            }
        }
        String message = lastFailure != null ? lastFailure.getMessage() : "Reconnect failed";
        gateway.recordConnectionError(base.id(), message);
        return new McpGateway.McpServerSummary(base.id(), false, 0, 0, false, message);
    }

    private void doReconnect(McpServerConfig base, Map<String, String> headers) {
        gateway.unregister(base.id());
        McpServerConfig effective = withHeaders(base, headers, true);
        Duration fallback = Duration.ofSeconds(properties.requestTimeoutSeconds());
        McpServerClient client = McpServerClientFactory.create(effective, fallback);
        gateway.registerClient(client);
    }

    private Optional<McpServerConfig> findConfig(String serverId) {
        return connectorResolver.findConfig(serverId);
    }

    private static McpServerConfig withHeaders(
            McpServerConfig server, Map<String, String> headers, boolean enabled) {
        return server.withHeaders(headers, enabled);
    }
}
