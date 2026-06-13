package com.huawei.ascend.runtime.engine.versatile;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a {@link VersatileHttpRequest} from an {@link AgentExecutionContext}.
 *
 * <h3>Structured metadata (recommended)</h3>
 * Users may nest versatile call parameters under a {@code "versatile"} key in
 * A2A metadata to clearly separate headers, query params, and body inputs:
 * <pre>{@code
 * "metadata": {
 *   "versatile": {
 *     "headers": {"x-language": "zh-cn"},
 *     "query":   {"type": "controller"},
 *     "inputs":  {"intent": "订酒店", "wap_userName": "张三"}
 *   }
 * }
 * }</pre>
 * When the {@code "versatile"} key is present its sub-maps are used directly.
 * When absent the adapter falls back to flat-metadata behaviour.
 *
 * <h3>Header priority (highest wins)</h3>
 * All paths respect the {@code versatile.passthrough-headers} allowlist.
 * <ol>
 *   <li>YAML {@code versatile.headers} — low priority (defaults)</li>
 *   <li>Flat A2A metadata matching the allowlist — medium</li>
 *   <li>Structured {@code versatile.headers} matching the allowlist — high priority</li>
 * </ol>
 *
 * <h3>Body</h3>
 * <pre>{@code {"inputs": {"query": <user-text>, ...versatile.inputs, ...flat metadata}}}</pre>
 *
 * <h3>Security note</h3>
 * HTTP headers are restricted to the {@code passthroughHeaders} allowlist
 * (flat) or the explicit {@code versatile.headers} map (structured).
 * Body fields are unrestricted — the remote endpoint validates its own inputs.
 */
public class VersatileMessageAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileMessageAdapter.class);

    static final String STRUCTURED_KEY = "versatile";
    private static final String STRUCTURED_HEADERS = "headers";
    private static final String STRUCTURED_QUERY = "query";
    private static final String STRUCTURED_INPUTS = "inputs";

    private final VersatileProperties properties;

    public VersatileMessageAdapter(VersatileProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public VersatileHttpRequest toRequest(AgentExecutionContext context) {
        String conversationId = context.getScope().sessionId();
        String url = buildUrl(context, conversationId);

        Map<String, String> headers = buildHeaders(context);
        Map<String, Object> body = buildBody(context);

        LOG.info("versatile request url={} headerKeys={} inputKeys={}",
                url, headers.keySet(),
                ((Map<?, ?>) body.getOrDefault("inputs", Map.of())).keySet());

        return new VersatileHttpRequest("POST", url, headers, body);
    }

    // ── URL assembly ──

    private String buildUrl(AgentExecutionContext context, String conversationId) {
        String baseUrl = properties.resolveUrl(conversationId);
        Map<String, Object> structuredQuery = structuredQuery(context);
        if (structuredQuery.isEmpty()) {
            return baseUrl;
        }
        // Structured query params override config query-params: strip the
        // query string from the resolved URL and rebuild from structured only.
        int queryIndex = baseUrl.indexOf('?');
        String baseWithoutQuery = queryIndex >= 0 ? baseUrl.substring(0, queryIndex) : baseUrl;
        StringBuilder sb = new StringBuilder(baseWithoutQuery);
        boolean first = true;
        for (var entry : structuredQuery.entrySet()) {
            sb.append(first ? '?' : '&');
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            first = false;
        }
        LOG.info("versatile url with structured query params={}", structuredQuery.keySet());
        return sb.toString();
    }

    // ── Header assembly ──

    private Map<String, String> buildHeaders(AgentExecutionContext context) {
        Map<String, String> finalHeaders = new LinkedHashMap<>();

        // Level 1: YAML pre-configured headers (low priority — defaults)
        Map<String, String> preConfig = properties.getHeaders();
        if (preConfig != null && !preConfig.isEmpty()) {
            finalHeaders.putAll(preConfig);
        }

        // Level 2: flat A2A metadata matching passthroughHeaders allowlist (medium priority)
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

        // Level 3: structured versatile.headers (high priority — user override).
        // Enforce the same passthroughHeaders allowlist as flat metadata so the
        // structured path cannot bypass the header security boundary.
        Map<String, Object> structuredHeaders = structuredHeaders(context);
        if (!structuredHeaders.isEmpty()) {
            List<String> allowed = properties.getPassthroughHeaders();
            for (var entry : structuredHeaders.entrySet()) {
                String key = entry.getKey();
                if (allowed != null && allowed.contains(key)) {
                    finalHeaders.put(toHeaderName(key), String.valueOf(entry.getValue()));
                } else {
                    LOG.warn("versatile structured header '{}' blocked — not in passthroughHeaders", key);
                }
            }
        }

        LOG.debug("versatile resolved headers: {}", finalHeaders.keySet());
        return finalHeaders;
    }

    // ── Body assembly ──

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildBody(AgentExecutionContext context) {
        Map<String, Object> vars = context.getVariables();
        LOG.info("versatile context vars keys={} hasVersatileKey={}",
                vars.keySet(), vars.containsKey(STRUCTURED_KEY));

        // Body = the "inputs" object directly as received from the remote
        // side (LLM assembled it per the versatile-request skill contract).
        // Merge in any framework-injected versatile.inputs metadata fields
        // (e.g. wap_userName) that the LLM does not need to know about.
        Map<String, Object> inputs = extractInputs(context);
        Map<String, Object> structuredInputs = structuredInputs(context);
        if (!structuredInputs.isEmpty()) {
            for (var entry : structuredInputs.entrySet()) {
                inputs.putIfAbsent(entry.getKey(), entry.getValue());
            }
            LOG.info("versatile structured inputs merged: {}", structuredInputs.keySet());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs);
        LOG.info("versatile body input keys={}", inputs.keySet());
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractInputs(AgentExecutionContext context) {
        Map<String, Object> vars = context.getVariables();
        if (vars != null) {
            Object inputs = vars.get("inputs");
            if (inputs instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    if (entry.getValue() != null) {
                        result.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return result;
            }
        }
        return new LinkedHashMap<>();
    }

    // ── Structured metadata accessors ──

    @SuppressWarnings("unchecked")
    private Map<String, Object> structuredMap(AgentExecutionContext context, String subKey) {
        Map<String, Object> vars = context.getVariables();
        if (vars == null) return Map.of();
        Object versatile = vars.get(STRUCTURED_KEY);
        if (!(versatile instanceof Map<?, ?> versatileMap)) return Map.of();
        Object sub = versatileMap.get(subKey);
        if (!(sub instanceof Map<?, ?> subMap)) return Map.of();
        return (Map<String, Object>) subMap;
    }

    private Map<String, Object> structuredHeaders(AgentExecutionContext context) {
        return structuredMap(context, STRUCTURED_HEADERS);
    }

    private Map<String, Object> structuredQuery(AgentExecutionContext context) {
        return structuredMap(context, STRUCTURED_QUERY);
    }

    private Map<String, Object> structuredInputs(AgentExecutionContext context) {
        return structuredMap(context, STRUCTURED_INPUTS);
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
