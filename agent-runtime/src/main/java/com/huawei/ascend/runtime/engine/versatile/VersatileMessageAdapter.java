package com.huawei.ascend.runtime.engine.versatile;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a {@link VersatileHttpRequest} from an {@link AgentExecutionContext}.
 *
 * <h3>URL</h3>
 * The {@code url-template} from {@link VersatileProperties} is resolved with
 * {@code {conversation_id} → sessionId} and any static {@code url-variables}.
 * {@code query-params} are appended as {@code ?key=value&...}.
 *
 * <h3>Headers (two-level priority)</h3>
 * <ol>
 *   <li>YAML {@code versatile.headers} — low priority</li>
 *   <li>A2A client metadata ({@code versatile.passthrough-headers} allowlist) — high priority</li>
 * </ol>
 *
 * <h3>Body</h3>
 * <pre>{@code {"inputs": {"query": <user-text>, ...<input-metadata-keys>}}}</pre>
 * Additional input fields are sourced from A2A metadata for keys listed in
 * {@code versatile.input-metadata-keys}.
 */
public class VersatileMessageAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileMessageAdapter.class);

    private final VersatileProperties properties;

    public VersatileMessageAdapter(VersatileProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public VersatileHttpRequest toRequest(AgentExecutionContext context) {
        String conversationId = context.getScope().sessionId();
        String url = properties.resolveUrl(conversationId);

        Map<String, String> headers = buildHeaders(context);
        Map<String, Object> body = buildBody(context);

        LOG.info("versatile request url={} headerKeys={} inputKeys={}",
                url, headers.keySet(),
                ((Map<?, ?>) body.getOrDefault("inputs", Map.of())).keySet());

        return new VersatileHttpRequest("POST", url, headers, body);
    }

    // ── Header assembly ──

    private Map<String, String> buildHeaders(AgentExecutionContext context) {
        Map<String, String> finalHeaders = new LinkedHashMap<>();

        // Level 1: YAML pre-configured headers (low priority)
        Map<String, String> preConfig = properties.getHeaders();
        if (preConfig != null && !preConfig.isEmpty()) {
            finalHeaders.putAll(preConfig);
        }

        // Level 2: A2A client passthrough (high priority — overrides on collision)
        List<String> passthroughKeys = properties.getPassthroughHeaders();
        if (passthroughKeys != null && !passthroughKeys.isEmpty()) {
            Map<String, Object> a2aMetadata = context.getVariables();
            for (String key : passthroughKeys) {
                Object value = a2aMetadata.get(key);
                if (value != null) {
                    finalHeaders.put(toHeaderName(key), String.valueOf(value));
                }
            }
        }

        LOG.debug("versatile resolved headers: {}", finalHeaders.keySet());
        return finalHeaders;
    }

    // ── Body assembly ──

    private Map<String, Object> buildBody(AgentExecutionContext context) {
        String query = context.lastUserText();
        LOG.info("versatile body query extracted chars={}", query.length());

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query);

        // Merge additional input fields from A2A metadata
        List<String> metadataKeys = properties.getInputMetadataKeys();
        if (metadataKeys != null && !metadataKeys.isEmpty()) {
            Map<String, Object> a2aMetadata = context.getVariables();
            for (String key : metadataKeys) {
                Object value = a2aMetadata.get(key);
                if (value != null) {
                    inputs.put(key, value);
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs);
        return body;
    }

    // ── Helpers ──

    private static String toHeaderName(String key) {
        if (key == null) {
            return "";
        }
        String[] parts = key.split("-");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('-');
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    sb.append(parts[i].substring(1));
                }
            }
        }
        return sb.toString();
    }
}
