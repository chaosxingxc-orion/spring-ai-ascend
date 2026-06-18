package com.huawei.ascend.runtime.engine.versatile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts versatile SSE text lines → framework-neutral {@link AgentExecutionResult}.
 *
 * <h3>Event mapping</h3>
 * <table>
 *   <tr><th>SSE event</th><th>Condition</th><th>AgentExecutionResult</th><th>Target</th></tr>
 *   <tr><td>{@code message}</td><td>text / summary present</td><td>{@code output(text)}</td><td>USER</td></tr>
 *   <tr><td>{@code workflow_finished}</td><td>—</td><td>{@code completed(assembledContent)}</td><td>LLM</td></tr>
 *   <tr><td>{@code exception}</td><td>—</td><td>{@code failed(code, message)}</td><td>BOTH</td></tr>
 *   <tr><td>{@code end}</td><td>—</td><td>{@code completed(assembledContent)}</td><td>LLM</td></tr>
 *   <tr><td>{@code connection_closed}</td><td>no prior end node</td><td>{@code interrupted(prompt)}</td><td>USER</td></tr>
 *   <tr><td>{@code connection_closed}</td><td>end node already received</td><td>filtered</td><td>—</td></tr>
 *   <tr><td>any <em>unknown</em> event</td><td>data contains text</td><td>{@code output(rawLine)}</td><td>USER</td></tr>
 *   <tr><td>control events</td><td>workflow_started, node_started, node_finished</td><td>filtered</td><td>—</td></tr>
 * </table>
 *
 * <h3>Node-type caching</h3>
 * Every {@code message} event is cached keyed by its {@code node_type} field from the
 * SSE data payload. When a terminal event ({@code end}, {@code workflow_finished})
 * arrives the adapter assembles the final completion content from the cache:
 * <ul>
 *   <li>If {@code versatile.result-node-type} is configured, only cached entries
 *       matching that node type (case-insensitive) are merged.</li>
 *   <li>Otherwise all cached entries are merged in insertion order.</li>
 * </ul>
 *
 * <h3>Interruption detection</h3>
 * A {@code connection_closed} event injected by {@link VersatileClient} signals that
 * the HTTP response stream ended. When no {@code node_type=End} (case-insensitive)
 * was observed before the close, the adapter emits {@code INTERRUPTED} so the A2A
 * task transitions to {@code INPUT_REQUIRED} per the standard A2A protocol.
 *
 * <h3>Target routing</h3>
 * Intermediate output → {@code Target.USER} (transparent to end user).
 * Final completion → {@code Target.LLM} (fed back to the calling LLM as tool result).
 * Interruption → {@code Target.USER} (shown to end user as prompt).
 */
public class VersatileStreamAdapter implements StreamAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileStreamAdapter.class);

    static final String ERROR_CODE_PREFIX = "VERSATILE_";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Events that carry no user-visible content — always filtered. */
    private static final Set<String> CONTROL_EVENTS = Set.of(
            "workflow_started", "node_started", "node_finished");

    private final String resultNodeType;
    private final List<VersatileProperties.ResultExtraction> resultExtractions;

    /** Backward-compatible no-arg constructor (resultNodeType = null → merge all). */
    public VersatileStreamAdapter() {
        this.resultNodeType = null;
        this.resultExtractions = List.of();
    }

    public VersatileStreamAdapter(VersatileProperties properties) {
        this.resultNodeType = properties != null ? properties.getResultNodeType() : null;
        this.resultExtractions = properties != null && properties.getResultExtractions() != null
                ? List.copyOf(properties.getResultExtractions()) : List.of();
    }

    @Override
    public Stream<AgentExecutionResult> adapt(Stream<?> rawResults) {
        final Map<String, List<String>> cache = new LinkedHashMap<>();
        final boolean[] hasEnd = {false};
        final boolean[] done = {false};
        final Map<String, Object> extracted = new LinkedHashMap<>();
        final Iterator<?> it = rawResults.iterator();

        return Stream.generate(() -> {
            if (done[0]) return null;
            while (it.hasNext()) {
                Object raw = it.next();
                AgentExecutionResult result = mapLine(raw, cache, hasEnd, extracted);
                if (result != null) {
                    if (isTerminal(result.type())) {
                        done[0] = true;
                    }
                    return result;
                }
            }
            return null;
        }).takeWhile(Objects::nonNull);
    }

    private static boolean isTerminal(AgentExecutionResult.Type type) {
        return type == AgentExecutionResult.Type.COMPLETED
                || type == AgentExecutionResult.Type.FAILED
                || type == AgentExecutionResult.Type.INTERRUPTED;
    }

    // ── Per-line mapping ──

    @SuppressWarnings("unchecked")
    private AgentExecutionResult mapLine(Object raw, Map<String, List<String>> cache,
            boolean[] hasEnd, Map<String, Object> extracted) {
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
            Map<String, Object> json = (Map<String, Object>) MAPPER.readValue(jsonStr, Map.class);
            if (json == null) return null;

            String event = (String) json.getOrDefault("event", "");
            if (event.isEmpty()) return null;

            if (CONTROL_EVENTS.contains(event)) return null;

            Map<String, Object> data = (Map<String, Object>) json.get("data");

            return switch (event) {
                case "message" -> handleMessage(data, cache, hasEnd);
                case "workflow_finished" -> handleWorkflowFinished(data, cache, extracted);
                case "exception" -> handleException(data);
                case "end" -> handleStreamTermination(cache, extracted, hasEnd);
                case "connection_closed" -> handleStreamTermination(cache, extracted, hasEnd);
                default -> handleUnknownEvent(line, event, json, data, extracted);
            };
        } catch (Exception e) {
            LOG.warn("versatile sse parse skipped: {}",
                    line.length() > 120 ? line.substring(0, 120) + "..." : line);
            return null;
        }
    }

    // ── Known event handlers ──

    private AgentExecutionResult handleMessage(Map<String, Object> data,
            Map<String, List<String>> cache, boolean[] hasEnd) {
        if (data == null) return null;

        // Check node_type for End detection (case-insensitive)
        String nodeType = asString(data.get("node_type"));
        if (!nodeType.isEmpty() && "end".equalsIgnoreCase(nodeType)) {
            hasEnd[0] = true;
        }

        String text = extractMessageText(data);
        if (text.isEmpty()) return null;

        // Cache by node_type
        String cacheKey = nodeType.isEmpty() ? "__default__" : nodeType;
        cache.computeIfAbsent(cacheKey, k -> new ArrayList<>()).add(text);

        return AgentExecutionResult.output(text, AgentExecutionResult.Target.USER);
    }

    private AgentExecutionResult handleWorkflowFinished(Map<String, Object> data,
            Map<String, List<String>> cache, Map<String, Object> extracted) {
        return completeFromCacheOrExtraction(cache, extracted, extractWorkflowContent(data));
    }

    /**
     * Unified termination for both the SSE {@code end} event and the
     * client-injected {@code connection_closed} marker. When a
     * {@code message(node_type=End)} was observed before termination
     * the stream completes normally; otherwise it is an interruption —
     * the workflow needs another round of input.
     */
    private AgentExecutionResult handleStreamTermination(
            Map<String, List<String>> cache, Map<String, Object> extracted, boolean[] hasEnd) {
        if (hasEnd[0]) {
            return completeFromCacheOrExtraction(cache, extracted, null);
        }
        LOG.info("versatile stream ended without End node_type — emitting INTERRUPTED");
        return AgentExecutionResult.interrupted("", AgentExecutionResult.Target.USER);
    }

    /** Tries extraction first; falls back to cache-assembled content. */
    private AgentExecutionResult completeFromCacheOrExtraction(
            Map<String, List<String>> cache, Map<String, Object> extracted, String extraContent) {
        String extractedContent = drainExtracted(extracted);
        if (!extractedContent.isEmpty()) {
            return AgentExecutionResult.completed(extractedContent, AgentExecutionResult.Target.LLM);
        }
        return AgentExecutionResult.completed(
                assembleFinalContent(cache, extraContent), AgentExecutionResult.Target.LLM);
    }

    private AgentExecutionResult handleException(Map<String, Object> data) {
        String code = data != null ? asString(data.get("code")) : "";
        String message = data != null ? asString(data.get("message")) : "";
        if (code.isEmpty()) code = "UNKNOWN";
        return AgentExecutionResult.failed(ERROR_CODE_PREFIX + code, message);
    }

    // ── Unknown event → passthrough or extraction ──

    private AgentExecutionResult handleUnknownEvent(String rawLine, String event,
            Map<String, Object> json, Map<String, Object> data, Map<String, Object> extracted) {
        // Check result extraction rules first. Each rule has:
        //   match — a keyword looked for anywhere in the raw SSE line
        //   get   — a key searched deeply in the parsed JSON tree
        if (!resultExtractions.isEmpty() && json != null) {
            for (VersatileProperties.ResultExtraction rule : resultExtractions) {
                String match = rule.getMatch();
                if (match == null || match.isBlank()) continue;
                if (!rawLine.contains(match)) continue;
                String getKey = rule.getGet();
                if (getKey == null || getKey.isBlank()) continue;
                Object value = deepFind(json, getKey);
                if (value != null) {
                    extracted.put(match, value);
                    LOG.info("versatile extracted result match='{}' get='{}'", match, getKey);
                    return null; // held until End
                }
            }
        }
        // Passthrough: unknown events without extraction rules
        if (data == null || data.isEmpty()) {
            LOG.debug("versatile unknown event '{}' with empty data — filtering", event);
            return null;
        }
        LOG.info("versatile passthrough event '{}' chars={}", event, rawLine.length());
        return AgentExecutionResult.output(rawLine, AgentExecutionResult.Target.USER);
    }

    // ── Caching and final content assembly ──

    /**
     * Assembles the final completion content from the cache, optionally merged
     * with an additional content string from workflow_finished outputs.
     *
     * @param cache node_type → collected text lines
     * @param extraContent additional content from workflow_finished outputs (may be empty/null)
     */
    private String assembleFinalContent(Map<String, List<String>> cache, String extraContent) {
        StringBuilder sb = new StringBuilder();
        for (var entry : cache.entrySet()) {
            if (resultNodeType != null && !resultNodeType.isBlank()
                    && !entry.getKey().equalsIgnoreCase(resultNodeType)) {
                continue;
            }
            for (String line : entry.getValue()) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(line);
            }
        }
        if (extraContent != null && !extraContent.isBlank()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(extraContent);
        }
        return sb.toString();
    }

    // ── Extraction helpers ──

    private static String extractMessageText(Map<String, Object> data) {
        Boolean isFinished = data.get("is_finished") instanceof Boolean b ? b : false;
        String summary = asString(data.get("summary"));
        String text = asString(data.get("text"));
        if (isFinished && !summary.isEmpty()) return summary;
        if (!text.isEmpty()) return text;
        if (!summary.isEmpty()) return summary;
        return "";
    }

    private static String extractWorkflowContent(Map<String, Object> data) {
        if (data == null) return "";
        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = (Map<String, Object>) data.get("outputs");
        if (outputs != null) {
            String content = asString(outputs.get("responseContent"));
            if (!content.isEmpty()) return content;
        }
        return "";
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // ── Result extraction helpers ──

    /**
     * Searches the JSON tree for a key (depth-first) and returns the first
     * matching value. This avoids forcing the user to specify a fixed path —
     * they only need to know the key name, not where it sits in the tree.
     */
    @SuppressWarnings("unchecked")
    private static Object deepFind(Map<String, Object> root, String key) {
        if (root == null || key == null) {
            return null;
        }
        // Check current level first
        if (root.containsKey(key)) {
            return root.get(key);
        }
        // Recurse into nested maps and lists
        for (Object value : root.values()) {
            if (value instanceof Map<?, ?> map) {
                Object found = deepFind((Map<String, Object>) map, key);
                if (found != null) return found;
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Object found = deepFind((Map<String, Object>) map, key);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Serializes all accumulated extracted content as a JSON string and
     * empties the accumulator. Returns the JSON, or empty string when
     * nothing was extracted.
     */
    private static String drainExtracted(Map<String, Object> extracted) {
        if (extracted.isEmpty()) {
            return "";
        }
        try {
            String json = MAPPER.writeValueAsString(extracted);
            extracted.clear();
            return json;
        } catch (Exception e) {
            LOG.warn("versatile failed to serialize extracted content: {}", e.getMessage());
            extracted.clear();
            return "";
        }
    }
}
