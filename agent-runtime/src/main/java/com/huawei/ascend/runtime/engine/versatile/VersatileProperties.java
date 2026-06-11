package com.huawei.ascend.runtime.engine.versatile;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the versatile REST proxy adapter.
 *
 * <p>Owns remote connection parameters, workflow identity, request-body
 * fixed fields, and the two-level header model:
 * <ol>
 *   <li>YAML pre-config {@code headers} (low priority)</li>
 *   <li>A2A client passthrough whitelist {@code passthrough-headers}
 *       (high priority — overrides YAML on key collision)</li>
 * </ol>
 */
@ConfigurationProperties(prefix = "versatile")
public class VersatileProperties {

    /** Remote host (no protocol prefix). */
    private String host;

    /** Remote port. */
    private int port = 30001;

    /** Use HTTPS when {@code true}. */
    private boolean ssl = true;

    /** Versatile workflow id, placed in the URL path. */
    private String workflowId;

    /** Versatile workspace id, placed as a URL query parameter. */
    private String workspaceId;

    /** HTTP connect / read timeout. */
    private Duration timeout = Duration.ofSeconds(30);

    /** Fixed version appended to the request body. */
    private long version;

    /** Fixed long-term-memory block appended to the request body. */
    private Map<String, Boolean> longTermMemory = Map.of(
            "enable_retrieve", true,
            "enable_extract", true);

    /**
     * YAML pre-configured HTTP headers (low priority).
     * Values can be overridden by A2A client passthrough headers on key collision.
     */
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * Allowlist of keys that the A2A client may supply via metadata.
     * Only keys present in this list are forwarded to the remote REST call.
     * Passthrough values override YAML {@link #headers} on key collision.
     */
    private List<String> passthroughHeaders = List.of();

    // ── Derived accessors ──

    public String baseUrl() {
        String scheme = ssl ? "https" : "http";
        return scheme + "://" + host + ":" + port;
    }

    public String workflowPath(String conversationId) {
        StringBuilder path = new StringBuilder("/v1/0/agent-manager/workflows/");
        path.append(workflowId);
        path.append("/conversations/");
        path.append(conversationId);
        if (workspaceId != null && !workspaceId.isBlank()) {
            path.append("?workspace_id=").append(workspaceId);
        }
        return path.toString();
    }

    // ── Getters / Setters ──

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public boolean isSsl() { return ssl; }
    public void setSsl(boolean ssl) { this.ssl = ssl; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Map<String, Boolean> getLongTermMemory() { return longTermMemory; }
    public void setLongTermMemory(Map<String, Boolean> longTermMemory) { this.longTermMemory = longTermMemory; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public List<String> getPassthroughHeaders() { return passthroughHeaders; }
    public void setPassthroughHeaders(List<String> passthroughHeaders) { this.passthroughHeaders = passthroughHeaders; }
}
