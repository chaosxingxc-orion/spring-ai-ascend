package com.huawei.ascend.edp.rail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huawei.ascend.edp.config.EdpAgentConfig;
import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Versatile Agent 委托调用 Rail。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>在 call_versatile 工具调用前标记远程 Agent 委托信息。</li>
 *     <li>通过 _skip_tool 跳过本地工具执行，避免当前 spike 阶段误执行未接入的真实 VA 调用。</li>
 *     <li>把远程调用元数据写入回调上下文，供后续 runtime 或级联处理扩展使用。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #VersatileInterruptRail(EdpConfig)}：创建 VA 委托 Rail。</li>
 *     <li>{@link #beforeToolCall(AgentCallbackContext)}：工具调用前回调入口。</li>
 *     <li>{@link #afterToolCall(AgentCallbackContext)}：工具调用后回调入口。</li>
 * </ul>
 */
public class VersatileInterruptRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersatileInterruptRail.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * EDP 专有配置，当前预留给 VA 委托策略和目标 Agent 配置使用。
     */
    private final EdpConfig edpConfig;

    private final EdpAgentConfig.Versatile versatileConfig;
    private final HttpClient httpClient;

    /**
     * 构造 VA 委托 Rail。
     *
     * @param edpConfig EDP 专有配置
     */
    public VersatileInterruptRail(EdpConfig edpConfig) {
        this(edpConfig, null);
    }

    public VersatileInterruptRail(EdpConfig edpConfig, EdpAgentConfig.Versatile versatileConfig) {
        this.edpConfig = edpConfig;
        this.versatileConfig = versatileConfig;
        Duration timeout = versatileConfig != null ? parseTimeout(versatileConfig.getTimeout()) : Duration.ofSeconds(30);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        // VA 与 MCP、ask_user 同属工具调用增强类 Rail，使用同一优先级。
        setPriority(50);
    }

    /**
     * 工具调用前回调。
     *
     * @param ctx OpenJiuwen 回调上下文，包含工具调用信息
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        // 关键判断：只有工具调用上下文才需要识别 VA 委托工具。
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = inputs.getToolName();

        // 关键判断：只拦截 call_versatile，其他工具直接放行。
        if ("call_versatile".equals(toolName)) {
            LOGGER.info("VersatileInterruptRail: intercepting call_versatile, direct call to versatile service");

            ctx.getExtra().put("_skip_tool", Boolean.TRUE);

            Map<String, Object> toolResult = callVersatile(inputs, ctx);
            inputs.setToolResult(toolResult);
            inputs.setToolMsg(ToolMessage.builder()
                    .content(toJson(toolResult))
                    .toolCallId(inputs.getToolCall() != null ? inputs.getToolCall().getId() : "call_versatile")
                    .build());
        }
    }

    private Map<String, Object> callVersatile(ToolCallInputs inputs, AgentCallbackContext ctx) {
        if (versatileConfig == null || versatileConfig.getUrl() == null || versatileConfig.getUrl().isBlank()) {
            return failedResult("versatile config is missing");
        }
        try {
            Map<String, Object> args = normalizeArgs(inputs);
            String conversationId = ctx.getSession() != null && ctx.getSession().getSessionId() != null
                    ? ctx.getSession().getSessionId() : "call-versatile-spike";
            String url = resolveUrl(conversationId);
            Map<String, Object> body = Map.of("inputs", buildInputs(args), "stream", true);
            String bodyJson = OBJECT_MAPPER.writeValueAsString(body);
            LOGGER.info("VersatileInterruptRail: request body {}", bodyJson);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(parseTimeout(versatileConfig.getTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8));
            Map<String, String> headers = versatileConfig.getHeaders() != null
                    ? versatileConfig.getHeaders() : Map.of();
            headers.forEach(builder::header);
            if (!hasHeader(headers, "content-type")) {
                builder.header("Content-Type", "application/json");
            }

            LOGGER.info("VersatileInterruptRail: POST {}", url);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            LOGGER.info("VersatileInterruptRail: response status={} body={}", response.statusCode(), abbreviate(response.body()));
            if (response.statusCode() >= 400) {
                return failedResult("HTTP " + response.statusCode() + ": " + response.body());
            }
            String content = normalizeContent(response.body());
            LOGGER.info("VersatileInterruptRail: normalized content {}", content);
            return Map.of("source", "versatile", "status", "completed", "content", content);
        } catch (Exception e) {
            LOGGER.warn("VersatileInterruptRail: direct call failed: {}", e.getMessage());
            return failedResult(e.getMessage());
        }
    }

    private Map<String, Object> normalizeArgs(ToolCallInputs inputs) {
        Map<String, Object> args = normalizeArgsObject(inputs.getToolArgs());
        if (args.isEmpty() && inputs.getToolCall() != null) {
            args = normalizeArgsObject(inputs.getToolCall().getArguments());
        }
        return args;
    }

    private Map<String, Object> normalizeArgsObject(Object toolArgs) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (toolArgs instanceof Map<?, ?> map) {
            map.forEach((key, value) -> args.put(String.valueOf(key), value));
            return args;
        }
        if (toolArgs instanceof String text && !text.isBlank()) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(text);
                if (node != null && node.isObject()) {
                    node.fields().forEachRemaining(entry -> args.put(entry.getKey(), OBJECT_MAPPER.convertValue(entry.getValue(), Object.class)));
                }
            } catch (Exception e) {
                LOGGER.warn("VersatileInterruptRail: failed to parse tool arguments: {}", text);
            }
        }
        return args;
    }

    private Map<String, Object> buildInputs(Map<String, Object> args) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        String query = String.valueOf(args.getOrDefault("query_description", ""));
        inputs.put("query", query);
        inputs.putAll(args);
        return inputs;
    }

    private String resolveUrl(String conversationId) {
        String resolved = versatileConfig.getUrl().replace("{conversation_id}", safePathSegment(conversationId));
        if (versatileConfig.getUrlVariables() != null) {
            for (Map.Entry<String, String> entry : versatileConfig.getUrlVariables().entrySet()) {
                resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        StringBuilder url = new StringBuilder(resolved);
        if (versatileConfig.getQueryParams() != null && !versatileConfig.getQueryParams().isEmpty()) {
            boolean first = !resolved.contains("?");
            for (Map.Entry<String, String> entry : versatileConfig.getQueryParams().entrySet()) {
                url.append(first ? '?' : '&').append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }
        return url.toString();
    }

    private String safePathSegment(String value) {
        return value.replace("/", "_").replace("\\", "_").replace("..", "_");
    }

    private boolean hasHeader(Map<String, String> headers, String name) {
        return headers.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }

    private Duration parseTimeout(String timeout) {
        if (timeout == null || timeout.isBlank()) {
            return Duration.ofSeconds(30);
        }
        String value = timeout.trim().toLowerCase();
        if (value.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(value.substring(0, value.length() - 2)));
        }
        if (value.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        if (value.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        return Duration.parse(timeout);
    }

    private String normalizeContent(String body) throws Exception {
        String raw = body != null ? body.trim() : "";
        if (raw.isBlank()) {
            return "{}";
        }
        JsonNode sseResult = extractFromSse(raw);
        if (sseResult != null) {
            return OBJECT_MAPPER.writeValueAsString(sseResult);
        }
        String candidate = raw;
        JsonNode node = OBJECT_MAPPER.readTree(candidate);
        if (node.isObject() || node.isArray()) {
            JsonNode extracted = extractStandardJsonContent(node);
            return OBJECT_MAPPER.writeValueAsString(extracted);
        }
        throw new IllegalArgumentException("versatile content is not standard JSON object or array");
    }

    private JsonNode extractFromSse(String raw) throws Exception {
        JsonNode lastJson = null;
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String payload = trimmed.substring(5).trim();
            if (payload.isBlank() || "[DONE]".equals(payload)) {
                continue;
            }
            JsonNode node = OBJECT_MAPPER.readTree(payload);
            lastJson = node;
            if (node.has("text")) {
                JsonNode textResult = parseTextJson(node.get("text"));
                if (textResult != null) {
                    return textResult;
                }
            }
            if (node.has("data")) {
                JsonNode extracted = extractStandardJsonContent(node);
                if (extracted.isObject() || extracted.isArray()) {
                    return extracted;
                }
            }
        }
        return lastJson;
    }

    private JsonNode extractStandardJsonContent(JsonNode node) throws Exception {
        if (node.has("content")) {
            return parseJsonNodeOrReturn(node.get("content"));
        }
        if (node.has("answer")) {
            return parseJsonNodeOrReturn(node.get("answer"));
        }
        if (node.has("data")) {
            JsonNode data = node.get("data");
            if (data.has("content")) {
                return parseJsonNodeOrReturn(data.get("content"));
            }
            if (data.has("answer")) {
                return parseJsonNodeOrReturn(data.get("answer"));
            }
            if (data.has("outputs")) {
                return parseJsonNodeOrReturn(data.get("outputs"));
            }
        }
        return node;
    }

    private JsonNode parseTextJson(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(node.asText());
            return parsed.isObject() || parsed.isArray() ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode parseJsonNodeOrReturn(JsonNode node) throws Exception {
        if (node == null || node.isNull()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        if (node.isObject() || node.isArray()) {
            return node;
        }
        if (node.isTextual()) {
            JsonNode parsed = OBJECT_MAPPER.readTree(node.asText());
            if (parsed.isObject() || parsed.isArray()) {
                return parsed;
            }
        }
        ObjectNode wrapper = OBJECT_MAPPER.createObjectNode();
        wrapper.set("value", node);
        return wrapper;
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 2000) {
            return value;
        }
        return value.substring(0, 2000) + "...(truncated)";
    }

    private Map<String, Object> failedResult(String error) {
        return Map.of("source", "versatile", "status", "failed", "error", error != null ? error : "unknown");
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 工具调用后回调。
     *
     * @param ctx OpenJiuwen 回调上下文，包含工具调用和工具结果信息
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        // 关键判断：只有工具调用上下文才需要识别 VA 委托工具。
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = inputs.getToolName();

        // 关键判断：只记录 call_versatile 完成事件。
        if ("call_versatile".equals(toolName)) {
            LOGGER.info("VersatileInterruptRail: call_versatile completed, cascade result received");
        }
    }
}
