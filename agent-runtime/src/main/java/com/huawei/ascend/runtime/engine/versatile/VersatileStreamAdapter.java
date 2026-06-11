package com.huawei.ascend.runtime.engine.versatile;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts versatile SSE text lines → framework-neutral {@link AgentExecutionResult}.
 *
 * <h3>Event mapping</h3>
 * <table>
 *   <tr><th>SSE event</th><th>Condition</th><th>AgentExecutionResult</th></tr>
 *   <tr><td>{@code message}</td><td>text non-empty</td><td>{@code output(text)}</td></tr>
 *   <tr><td>{@code message}</td><td>is_finished=true</td><td>{@code output(summary)}</td></tr>
 *   <tr><td>{@code workflow_finished}</td><td>—</td><td>{@code completed(outputs.responseContent)}</td></tr>
 *   <tr><td>{@code end}</td><td>no prior workflow_finished</td><td>{@code completed("")}</td></tr>
 *   <tr><td>{@code exception}</td><td>—</td><td>{@code failed(code, message)}</td></tr>
 *   <tr><td>others</td><td>—</td><td>filtered (no result emitted)</td></tr>
 * </table>
 */
public class VersatileStreamAdapter implements StreamAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileStreamAdapter.class);

    static final String ERROR_CODE_PREFIX = "VERSATILE_";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Stream<AgentExecutionResult> adapt(Stream<?> rawResults) {
        return rawResults
                .map(this::mapRawLine)
                .filter(result -> result != null);
    }

    private AgentExecutionResult mapRawLine(Object raw) {
        if (raw == null) {
            return null;
        }
        String line = String.valueOf(raw).trim();
        if (line.isEmpty()) {
            return null;
        }

        // Strip SSE "data:" prefix if present
        String jsonStr = line;
        if (line.startsWith("data:")) {
            jsonStr = line.substring(5).trim();
            if (jsonStr.isEmpty()) {
                return null;
            }
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = (Map<String, Object>) MAPPER.readValue(jsonStr, Map.class);

            if (json == null) {
                return null;
            }

            String event = (String) json.getOrDefault("event", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) json.get("data");

            return switch (event) {
                case "message" -> handleMessage(data);
                case "workflow_finished" -> handleWorkflowFinished(data);
                case "exception" -> handleException(data);
                case "end" -> AgentExecutionResult.completed("");
                // Intermediate events: workflow_started, node_started, node_finished — filtered
                default -> null;
            };
        } catch (Exception e) {
            LOG.warn("versatile sse parse failed, skipping line: {}", line.length() > 120 ? line.substring(0, 120) + "..." : line);
            return null;
        }
    }

    private AgentExecutionResult handleMessage(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        // Prefer summary when is_finished, otherwise text
        Boolean isFinished = data.get("is_finished") instanceof Boolean b ? b : false;
        String summary = asString(data.get("summary"));
        String text = asString(data.get("text"));

        if (isFinished && !summary.isEmpty()) {
            return AgentExecutionResult.output(summary);
        }
        if (!text.isEmpty()) {
            return AgentExecutionResult.output(text);
        }
        if (!summary.isEmpty()) {
            return AgentExecutionResult.output(summary);
        }
        return null;
    }

    private AgentExecutionResult handleWorkflowFinished(Map<String, Object> data) {
        if (data == null) {
            return AgentExecutionResult.completed("");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = (Map<String, Object>) data.get("outputs");
        if (outputs != null) {
            String responseContent = asString(outputs.get("responseContent"));
            if (!responseContent.isEmpty()) {
                return AgentExecutionResult.completed(responseContent);
            }
        }
        return AgentExecutionResult.completed("");
    }

    private AgentExecutionResult handleException(Map<String, Object> data) {
        String code = data != null ? asString(data.get("code")) : "";
        String message = data != null ? asString(data.get("message")) : "";
        if (code.isEmpty()) {
            code = "UNKNOWN";
        }
        return AgentExecutionResult.failed(ERROR_CODE_PREFIX + code, message);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
