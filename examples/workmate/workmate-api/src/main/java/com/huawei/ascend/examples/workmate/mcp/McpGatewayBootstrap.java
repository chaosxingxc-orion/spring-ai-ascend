package com.huawei.ascend.examples.workmate.mcp;

import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import com.huawei.ascend.examples.workmate.connector.ConnectorCredentialStore;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class McpGatewayBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(McpGatewayBootstrap.class);

    private final WorkmateMcpProperties properties;
    private final McpGateway gateway;
    private final ConnectorCredentialStore credentialStore;

    public McpGatewayBootstrap(
            WorkmateMcpProperties properties,
            McpGateway gateway,
            ConnectorCredentialStore credentialStore) {
        this.properties = properties;
        this.gateway = gateway;
        this.credentialStore = credentialStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            LOG.info("MCP gateway disabled (workmate.mcp.enabled=false)");
            return;
        }
        Duration fallback = Duration.ofSeconds(properties.requestTimeoutSeconds());
        for (McpServerConfig server : properties.servers()) {
            if (!server.enabled()) {
                continue;
            }
            try {
                Map<String, String> headers = new HashMap<>(server.headers());
                credentialStore.find(server.id()).ifPresent(headers::putAll);
                McpServerConfig effective = server.withHeaders(headers, server.enabled());
                McpServerClient client = McpServerClientFactory.create(effective, fallback);
                gateway.register(client);
                LOG.info("Registered MCP server id={} tools={}", server.id(), client.listTools().size());
            } catch (RuntimeException ex) {
                LOG.warn("Failed to start MCP server id={}: {}", server.id(), ex.getMessage());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        gateway.closeAll();
    }
}
