package com.huawei.ascend.examples.workmate.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.mcp")
public record WorkmateMcpProperties(
        boolean enabled,
        long requestTimeoutSeconds,
        int toolsLimitWarning,
        int reconnectMaxAttempts,
        long reconnectInitialBackoffMs,
        List<McpServerConfig> servers) {

    public WorkmateMcpProperties {
        if (requestTimeoutSeconds <= 0) {
            requestTimeoutSeconds = 30;
        }
        if (toolsLimitWarning <= 0) {
            toolsLimitWarning = 50;
        }
        if (reconnectMaxAttempts <= 0) {
            reconnectMaxAttempts = 3;
        }
        if (reconnectInitialBackoffMs <= 0) {
            reconnectInitialBackoffMs = 500;
        }
        if (servers == null) {
            servers = List.of();
        }
    }

    public record McpServerConfig(
            String id,
            boolean enabled,
            String transport,
            String command,
            List<String> args,
            String url,
            String endpoint,
            Map<String, String> headers,
            List<String> toolAllowlist,
            Map<String, String> env,
            long timeoutSeconds) {

        public McpServerConfig {
            if (args == null) {
                args = List.of();
            }
            if (headers == null) {
                headers = Map.of();
            }
            if (toolAllowlist == null) {
                toolAllowlist = List.of();
            }
            if (env == null) {
                env = Map.of();
            }
            if (timeoutSeconds < 0) {
                timeoutSeconds = 0;
            }
        }

        public McpServerConfig withHeaders(Map<String, String> headers, boolean enabled) {
            return new McpServerConfig(
                    id,
                    enabled,
                    transport,
                    command,
                    args,
                    url,
                    endpoint,
                    headers,
                    toolAllowlist,
                    env,
                    timeoutSeconds);
        }

        public Duration requestTimeout(Duration fallback) {
            if (timeoutSeconds > 0) {
                return Duration.ofSeconds(timeoutSeconds);
            }
            return fallback;
        }
    }
}
