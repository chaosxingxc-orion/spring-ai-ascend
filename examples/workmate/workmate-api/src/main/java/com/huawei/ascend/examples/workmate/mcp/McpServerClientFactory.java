package com.huawei.ascend.examples.workmate.mcp;

import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties.McpServerConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class McpServerClientFactory {

    private McpServerClientFactory() {
    }

    static McpServerClient create(McpServerConfig server, Duration fallbackTimeout) {
        Duration timeout = server.requestTimeout(fallbackTimeout);
        McpServerClient client;
        if (server.url() != null && !server.url().isBlank()) {
            validateHttpHeaders(server);
            client = new HttpMcpServerClient(server.id(), server, timeout);
        } else if (server.command() == null || server.command().isBlank()) {
            throw new IllegalArgumentException(
                    "MCP server " + server.id() + " requires either url or command");
        } else {
            client = new StdioMcpServerClient(
                    server.id(), server.command(), server.args(), server.env(), timeout);
        }
        List<String> allowlist = McpToolAllowlistResolver.resolve(server.id(), server.toolAllowlist());
        if (allowlist.isEmpty()) {
            return client;
        }
        return new FilteredMcpServerClient(client, allowlist);
    }

    private static void validateHttpHeaders(McpServerConfig server) {
        for (Map.Entry<String, String> entry : server.headers().entrySet()) {
            if ("x-api-key".equalsIgnoreCase(entry.getKey())
                    && (entry.getValue() == null || entry.getValue().isBlank())) {
                throw new IllegalStateException(
                        "MCP server " + server.id() + " requires x-api-key header (set WORKMATE_MCP_QIEMAN_API_KEY)");
            }
        }
    }
}
