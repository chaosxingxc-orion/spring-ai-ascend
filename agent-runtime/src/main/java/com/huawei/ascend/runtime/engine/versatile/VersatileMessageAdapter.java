package com.huawei.ascend.runtime.engine.versatile;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a {@link VersatileHttpRequest} from an {@link AgentExecutionContext}.
 *
 * <h3>URL</h3>
 * {@code https://{host}:{port}/v1/0/agent-manager/workflows/{workflowId}/conversations/{sessionId}?workspace_id={workspaceId}}
 *
 * <h3>Headers (two-level priority)</h3>
 * <ol>
 *   <li>YAML pre-config {@code versatile.headers} — low priority</li>
 *   <li>A2A client metadata, filtered by {@code versatile.passthrough-headers} — high priority, overrides YAML</li>
 * </ol>
 *
 * <h3>Body</h3>
 * {@code {inputs:{query}, memory_inputs:{}, globals:{}, plugin_configs:[], version, long_term_memory}}
 */
public class VersatileMessageAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileMessageAdapter.class);

    private final VersatileProperties properties;

    public VersatileMessageAdapter(VersatileProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Build the full REST request from the execution context.
     */
    public VersatileHttpRequest toRequest(AgentExecutionContext context) {
        String conversationId = context.getScope().sessionId();
        String url = properties.workflowPath(conversationId);

        Map<String, String> headers = buildHeaders(context);
        Map<String, Object> body = buildBody(context);

        LOG.info("versatile request url={} headerKeys={} bodyQuery={}",
                url, headers.keySet(), body.getOrDefault("inputs", Map.of()));

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
        String query = lastUserText(context);
        LOG.info("versatile body query extracted chars={}", query.length());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", java.util.Map.of("query", query));
        body.put("memory_inputs", java.util.Map.of());
        body.put("globals", java.util.Map.of());
        body.put("plugin_configs", List.of());
        body.put("version", properties.getVersion());
        body.put("long_term_memory", properties.getLongTermMemory());
        return body;
    }

    // ── Helpers ──

    /**
     * Extract text from the last user message in the conversation.
     */
    static String lastUserText(AgentExecutionContext context) {
        List<Message> messages = context.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message != null && message.role() == Message.Role.ROLE_USER) {
                return messageText(message);
            }
        }
        return messageText(messages.get(messages.size() - 1));
    }

    private static String messageText(Message msg) {
        if (msg == null || msg.parts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var part : msg.parts()) {
            if (part instanceof TextPart tp) {
                sb.append(tp.text());
            }
        }
        return sb.toString();
    }

    /**
     * Convert a hyphenated metadata key to an HTTP header name.
     * Example: {@code x-invoke-mode} → {@code X-Invoke-Mode}.
     */
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
