package com.huawei.ascend.runtime.engine.versatile;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Configuration for the versatile REST proxy adapter.
 *
 * <h3>URL resolution</h3>
 * {@code url} is a full template (scheme + host + path) with {@code {placeholder}}
 * substitution. {@code {conversation_id}} is always resolved from the A2A session
 * id; all other placeholders come from {@code url-variables}.
 * {@code query-params} are appended as {@code ?key=value&...}.
 *
 * <h3>Header model (two-level priority)</h3>
 * <ol>
 *   <li>YAML pre-config {@code headers} (low priority)</li>
 *   <li>A2A client passthrough (high priority — overrides on key collision)</li>
 * </ol>
 */
@ConfigurationProperties(prefix = "versatile")
public class VersatileProperties {

    /**
     * Full URL template including scheme, host, and path.
     * {@code {conversation_id}} is resolved at runtime from the A2A session id;
     * other {@code {placeholder}} values come from {@link #urlVariables}.
     *
     * <p>Examples:
     * <pre>{@code https://100.93.15.185:30001/v1/0/agent-manager/workflows/{workflow_id}/conversations/{conversation_id}}
     * {@code http://7.213.200.213:3001/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}}</pre>
     */
    private String url = "https://100.93.15.185:30001/v1/0/agent-manager/workflows/{workflow_id}/conversations/{conversation_id}";

    /** HTTP connect / read timeout. */
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * Static values for URL template placeholders (all except
     * {@code {conversation_id}}, which is always the session id).
     */
    private Map<String, String> urlVariables = new LinkedHashMap<>();

    /** Static URL query parameters appended after the path. */
    private Map<String, String> queryParams = new LinkedHashMap<>();

    /**
     * YAML pre-configured HTTP headers (low priority).
     * Values can be overridden by A2A client passthrough headers on key collision.
     */
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * Allowlist of keys that the A2A client may supply via metadata.
     * Only keys present in this list are forwarded to the remote REST request.
     * Passthrough values override YAML {@link #headers} on key collision.
     */
    private List<String> passthroughHeaders = List.of();

    /**
     * Keys to extract from A2A client metadata and merge into the request
     * body's {@code inputs} map (alongside the mandatory {@code query}).
     */
    private List<String> inputMetadataKeys = List.of();

    // ── Derived accessors ──

    /**
     * Resolve the full target URL including scheme, host, path, and query string.
     *
     * @param conversationId value for the {@code {conversation_id}} placeholder
     */
    public String resolveUrl(String conversationId) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        if (conversationId.contains("/") || conversationId.contains("\\")
                || conversationId.contains("..")) {
            throw new IllegalArgumentException("conversationId contains invalid path traversal characters");
        }
        String resolved = url.replace("{conversation_id}", conversationId);
        if (urlVariables != null) {
            for (var entry : urlVariables.entrySet()) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        StringBuilder sb = new StringBuilder(resolved);
        if (queryParams != null && !queryParams.isEmpty()) {
            boolean first = !resolved.contains("?");
            for (var entry : queryParams.entrySet()) {
                sb.append(first ? '?' : '&');
                sb.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }
        return sb.toString();
    }

    // ── Getters / Setters ──

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }

    public Map<String, String> getUrlVariables() { return urlVariables; }
    public void setUrlVariables(Map<String, String> urlVariables) { this.urlVariables = urlVariables; }

    public Map<String, String> getQueryParams() { return queryParams; }
    public void setQueryParams(Map<String, String> queryParams) { this.queryParams = queryParams; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public List<String> getPassthroughHeaders() { return passthroughHeaders; }
    public void setPassthroughHeaders(List<String> passthroughHeaders) { this.passthroughHeaders = passthroughHeaders; }

    public List<String> getInputMetadataKeys() { return inputMetadataKeys; }
    public void setInputMetadataKeys(List<String> inputMetadataKeys) { this.inputMetadataKeys = inputMetadataKeys; }
}
