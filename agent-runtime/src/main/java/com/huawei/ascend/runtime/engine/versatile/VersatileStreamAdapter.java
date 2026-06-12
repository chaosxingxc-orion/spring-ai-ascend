package com.huawei.ascend.runtime.engine.versatile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts versatile SSE text lines → framework-neutral {@link AgentExecutionResult}.
 *
 * <h3>Event mapping</h3>
 * <table>
 *   <tr><th>SSE event</th><th>Condition</th><th>AgentExecutionResult</th></tr>
 *   <tr><td>{@code message}</td><td>text / summary present</td><td>{@code output(text)}</td></tr>
 *   <tr><td>{@code workflow_finished}</td><td>—</td><td>{@code completed(responseContent)}</td></tr>
 *   <tr><td>{@code exception}</td><td>—</td><td>{@code failed(code, message)}</td></tr>
 *   <tr><td>{@code end}</td><td>—</td><td>{@code completed("")}</td></tr>
 *   <tr><td>any <em>unknown</em> event</td><td>data contains text</td><td>{@code output(json)}</td></tr>
 *   <tr><td>control events</td><td>workflow_started, node_*</td><td>filtered</td></tr>
 * </table>
 *
 * <p>Unknown events (e.g. {@code hotels_info}) are treated as output —
 * their {@code data} object is serialized to JSON and emitted as a single
 * {@code output} result so the A2A client receives the structured payload.
 */
public class VersatileStreamAdapter implements StreamAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileStreamAdapter.class);

    static final String ERROR_CODE_PREFIX = "VERSATILE_";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Events that carry no user-visible content — always filtered. */
    private static final Set<String> CONTROL_EVENTS = Set.of(
            "workflow_started", "node_started", "node_finished");

    @Override
    public Stream<AgentExecutionResult> adapt(Stream<?> rawResults) {
        return rawResults
                .map(this::mapRawLine)
                .filter(result -> result != null);
    }

    private AgentExecutionResult mapRawLine(Object raw) {
        if (raw == null) return null;
        String line = String.valueOf(raw).trim();
        if (line.isEmpty()) return null;

        // Strip SSE "data:" prefix
        String jsonStr = line;
        if (line.startsWith("data:")) {
            jsonStr = line.substring(5).trim();
            if (jsonStr.isEmpty()) return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = (Map<String, Object>) MAPPER.readValue(jsonStr, Map.class);
            if (json == null) return null;

            String event = (String) json.getOrDefault("event", "");
            if (event.isEmpty()) return null;

            if (CONTROL_EVENTS.contains(event)) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) json.get("data");

            return switch (event) {
                case "message" -> handleMessage(data);
                case "workflow_finished" -> handleWorkflowFinished(data);
                case "exception" -> handleException(data);
                case "end" -> AgentExecutionResult.completed("");
                // Any unknown event → passthrough the raw SSE line as-is
                default -> handleUnknownEvent(line, event, data);
            };
        } catch (Exception e) {
            LOG.warn("versatile sse parse skipped: {}",
                    line.length() > 120 ? line.substring(0, 120) + "..." : line);
            return null;
        }
    }

    // ── Known event handlers ──

    private AgentExecutionResult handleMessage(Map<String, Object> data) {
        if (data == null) return null;
        Boolean isFinished = data.get("is_finished") instanceof Boolean b ? b : false;
        String summary = asString(data.get("summary"));
        String text = asString(data.get("text"));
        if (isFinished && !summary.isEmpty()) return AgentExecutionResult.output(summary);
        if (!text.isEmpty()) return AgentExecutionResult.output(text);
        if (!summary.isEmpty()) return AgentExecutionResult.output(summary);
        return null;
    }

    private AgentExecutionResult handleWorkflowFinished(Map<String, Object> data) {
        if (data == null) return AgentExecutionResult.completed("");
        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = (Map<String, Object>) data.get("outputs");
        if (outputs != null) {
            String content = asString(outputs.get("responseContent"));
            if (!content.isEmpty()) return AgentExecutionResult.completed(content);
        }
        return AgentExecutionResult.completed("");
    }

    private AgentExecutionResult handleException(Map<String, Object> data) {
        String code = data != null ? asString(data.get("code")) : "";
        String message = data != null ? asString(data.get("message")) : "";
        if (code.isEmpty()) code = "UNKNOWN";
        return AgentExecutionResult.failed(ERROR_CODE_PREFIX + code, message);
    }

    // ── Unknown event → raw passthrough ──

    /**
     * Passthrough: emit the original SSE line unchanged so the A2A client
     * sees exactly what the remote server returned (same as {@code curl}).
     */
    private AgentExecutionResult handleUnknownEvent(String rawLine, String event, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            LOG.debug("versatile unknown event '{}' with empty data — filtering", event);
            return null;
        }
        // Emit the original line (with "data:" prefix already stripped) as-is
        LOG.info("versatile passthrough event '{}' chars={}", event, rawLine.length());
        return AgentExecutionResult.output(rawLine);
    }

    // ── Helpers ──

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
