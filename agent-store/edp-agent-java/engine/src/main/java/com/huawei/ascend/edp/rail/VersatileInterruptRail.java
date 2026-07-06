package com.huawei.ascend.edp.rail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huawei.ascend.edp.channel.ToolDataChannel;
import com.huawei.ascend.edp.channel.ToolDataKey;
import com.huawei.ascend.edp.channel.ToolDataKeyFactory;
import com.huawei.ascend.edp.config.EdpaSpringBootConfig;
import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.config.ScriptConstants;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Versatile Agent 委托调用 Rail。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>在 call_versatile 工具调用前拦截并直连 Versatile REST 或 adapter A2A。</li>
 *     <li>解析 adapter SSE 响应，分离 USER 透传节点与 LLM 终态内容。</li>
 *     <li>adapter 请求用户输入时抛出 {@link ToolInterruptException}，由 runtime 续传。</li>
 *     <li>续传恢复时从 {@link ToolInterruptionState#RESUME_USER_INPUT_KEY} 回填工具结果。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #beforeToolCall(AgentCallbackContext)}：工具调用前回调入口。</li>
 *     <li>{@link #invokeWithInputs(Map, String)}：供 handler 层 Versatile 菜单续传直接调用。</li>
 *     <li>{@link VersatilePassthroughBuffer}：与 {@link com.huawei.ascend.edp.handler.EdpaRuntimeHandler}
 *         共享的会话级 USER 节点缓冲。</li>
 * </ul>
 */
public class VersatileInterruptRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersatileInterruptRail.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * EDP 专有配置，当前预留给 VA 委托策略和目标 Agent 配置使用。
     */
    private final EdpConfig edpConfig;

    private final EdpaSpringBootConfig.VersatileConfig versatileConfig;
    private final ToolDataChannel toolDataChannel;
    /** 与 EdpaRuntimeHandler 共享，存放 adapter 返回的完整 Versatile message JSON。 */
    private final VersatilePassthroughBuffer passthroughBuffer;
    private final HttpClient httpClient;

    /**
     * 构造 VA 委托 Rail。
     *
     * @param edpConfig EDP 专有配置
     */
    public VersatileInterruptRail(EdpConfig edpConfig) {
        this(edpConfig, null, new ToolDataChannel());
    }

    public VersatileInterruptRail(EdpConfig edpConfig, EdpaSpringBootConfig.VersatileConfig versatileConfig) {
        this(edpConfig, versatileConfig, new ToolDataChannel());
    }

    public VersatileInterruptRail(EdpConfig edpConfig, EdpaSpringBootConfig.VersatileConfig versatileConfig,
            ToolDataChannel toolDataChannel) {
        this(edpConfig, versatileConfig, toolDataChannel, new VersatilePassthroughBuffer());
    }

    public VersatileInterruptRail(EdpConfig edpConfig, EdpaSpringBootConfig.VersatileConfig versatileConfig,
            ToolDataChannel toolDataChannel, VersatilePassthroughBuffer passthroughBuffer) {
        this.edpConfig = edpConfig;
        this.versatileConfig = versatileConfig;
        this.toolDataChannel = toolDataChannel != null ? toolDataChannel : new ToolDataChannel();
        this.passthroughBuffer = passthroughBuffer != null ? passthroughBuffer : new VersatilePassthroughBuffer();
        Duration timeout = versatileConfig != null ? parseTimeout(versatileConfig.getTimeout()) : Duration.ofSeconds(30);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        // VA 与 MCP、ask_user 同属工具调用增强类 Rail，使用同一优先级。
        setPriority(85);
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
            String toolCallId = toolCallId(inputs);
            // 续传路径：用户已提交菜单确认等输入，直接回填工具结果，不再重复调 adapter。
            Object resumeInput = resolveResumeInput(ctx, toolCallId);
            if (resumeInput != null) {
                LOGGER.info("VersatileInterruptRail: resuming call_versatile with adapter result, toolCallId={}",
                        toolCallId);
                ctx.getExtra().put(ScriptConstants.KEY_SKIP_TOOL, Boolean.TRUE);
                Object toolResult = normalizeResumeToolResult(resumeInput);
                inputs.setToolResult(toolResult);
                inputs.setToolMsg(ToolMessage.builder()
                        .content(toJson(toolResult))
                        .toolCallId(toolCallId)
                        .build());
                return;
            }
            LOGGER.info("VersatileInterruptRail: intercepting call_versatile, direct call to versatile service");

            ctx.getExtra().put(ScriptConstants.KEY_SKIP_TOOL, Boolean.TRUE);

            Map<String, Object> toolResult = callVersatile(inputs, ctx);
            if (isInputRequired(toolResult)) {
                // adapter 进入 INPUT_REQUIRED：先刷透传节点，再中断等待用户确认。
                throw inputRequiredInterrupt(ctx, inputs, toolResult);
            }
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
            Map<String, Object> versatileInputs = buildInputs(args, ctx);
            return invokeWithInputs(versatileInputs, conversationId);
        } catch (Exception e) {
            LOGGER.warn("VersatileInterruptRail: direct call failed: {}", e.getMessage());
            return failedResult(e.getMessage());
        }
    }

    private String toolCallId(ToolCallInputs inputs) {
        return inputs.getToolCall() != null && inputs.getToolCall().getId() != null
                && !inputs.getToolCall().getId().isBlank()
                ? inputs.getToolCall().getId() : "call_versatile";
    }

    private Object resolveResumeInput(AgentCallbackContext ctx, String toolCallId) {
        // runtime 在中断恢复时把用户输入写入 extra；优先按 toolCallId 精确匹配。
        Object rawInput = ctx.getExtra().get(ToolInterruptionState.RESUME_USER_INPUT_KEY);
        if (rawInput instanceof InteractiveInput interactiveInput) {
            Map<String, Object> userInputs = interactiveInput.getUserInputs();
            if (toolCallId != null && !toolCallId.isBlank() && userInputs.containsKey(toolCallId)) {
                return userInputs.get(toolCallId);
            }
            return interactiveInput.getRawInputs();
        }
        if (rawInput instanceof Map<?, ?> map && toolCallId != null && !toolCallId.isBlank()
                && map.containsKey(toolCallId)) {
            return map.get(toolCallId);
        }
        return rawInput;
    }

    private Object normalizeResumeToolResult(Object resumeInput) {
        if (resumeInput instanceof String text && !text.isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() { });
            } catch (Exception e) {
                return Map.of("source", "versatile", "status", "completed", "content", text);
            }
        }
        return resumeInput;
    }

    /**
     * 统一 Versatile 调用入口：优先 adapter A2A，否则 REST 直连。
     * 调用完成后把 passthrough_nodes 写入共享缓冲供上层流式刷出。
     */
    public Map<String, Object> invokeWithInputs(Map<String, Object> versatileInputs, String conversationId) {
        if (versatileConfig == null) {
            return failedResult("versatile config is missing");
        }
        boolean hasAdapterA2a = versatileConfig.getAdapterA2aUrl() != null
                && !versatileConfig.getAdapterA2aUrl().isBlank();
        boolean hasDirectUrl = versatileConfig.getUrl() != null && !versatileConfig.getUrl().isBlank();
        if (!hasAdapterA2a && !hasDirectUrl) {
            return failedResult("versatile config is missing");
        }
        try {
            Map<String, Object> result = hasAdapterA2a
                    ? callVersatileAdapterA2a(versatileInputs, conversationId)
                    : callVersatileDirect(versatileInputs, conversationId);
            storePassthroughNodes(conversationId, result);
            return result;
        } catch (Exception e) {
            LOGGER.warn("VersatileInterruptRail: direct call failed: {}", e.getMessage());
            return failedResult(e.getMessage());
        }
    }

    private Map<String, Object> callVersatileDirect(Map<String, Object> versatileInputs, String conversationId)
            throws Exception {
        String url = resolveUrl(conversationId);
        Map<String, Object> body = Map.of("inputs", versatileInputs, "stream", true);
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
    }

    private Map<String, Object> normalizeArgs(ToolCallInputs inputs) {
        Map<String, Object> args = normalizeArgsObject(inputs.getToolArgs());
        if (args.isEmpty() && inputs.getToolCall() != null) {
            args = normalizeArgsObject(inputs.getToolCall().getArguments());
        }
        return args;
    }

    /** 通过 adapter-versatile-agent-java 的 A2A SendStreamingMessage 发起 SSE 调用。 */
    private Map<String, Object> callVersatileAdapterA2a(Map<String, Object> versatileInputs, String conversationId)
            throws Exception {
        String adapterUrl = versatileConfig.getAdapterA2aUrl();
        String messageText = OBJECT_MAPPER.writeValueAsString(Map.of("inputs", versatileInputs));
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "ROLE_USER");
        message.put("messageId", "msg-" + UUID.randomUUID());
        message.put("contextId", conversationId);
        message.put("parts", List.of(Map.of("text", messageText)));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", "edp-agent");
        metadata.put("agentId", "edp-agent");
        metadata.put("versatile", Map.of("inputs", versatileInputs));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("metadata", metadata);
        params.put("message", message);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("method", "SendStreamingMessage");
        requestBody.put("id", "call-versatile-" + UUID.randomUUID());
        requestBody.put("params", params);

        String bodyJson = OBJECT_MAPPER.writeValueAsString(requestBody);
        LOGGER.info("VersatileInterruptRail: POST adapter A2A {}", adapterUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(adapterUrl))
                .timeout(parseTimeout(versatileConfig.getTimeout()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        LOGGER.info("VersatileInterruptRail: adapter A2A status={} body={}",
                response.statusCode(), abbreviate(response.body()));
        if (response.statusCode() >= 400) {
            return failedResult("adapter A2A HTTP " + response.statusCode() + ": " + response.body());
        }
        return normalizeA2aAdapterResponse(response.body());
    }

    /**
     * 解析 adapter A2A SSE 响应体。
     * artifactUpdate 中的 text 为 USER 透传节点；statusUpdate 中的 text 为 LLM 终态内容。
     */
    private Map<String, Object> normalizeA2aAdapterResponse(String body) throws Exception {
        List<String> passthroughNodes = new ArrayList<>();
        String completedContent = "";
        String state = "";
        for (String line : body != null ? body.split("\\R") : new String[0]) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String payload = trimmed.substring(5).trim();
            if (payload.isBlank() || "[DONE]".equals(payload)) {
                continue;
            }
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            JsonNode result = root.path("result");
            state = extractA2aState(result, state);
            String artifactText = extractA2aArtifactText(result);
            if (!artifactText.isBlank()) {
                passthroughNodes.add(artifactText);
            }
            String terminalText = extractA2aStatusText(result);
            if (!terminalText.isBlank()) {
                completedContent = terminalText;
            }
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("source", "versatile");
        normalized.put("status", state.equalsIgnoreCase("TASK_STATE_INPUT_REQUIRED") ? "input_required" : "completed");
        normalized.put("content", completedContent);
        normalized.put("passthrough_nodes", passthroughNodes);
        return normalized;
    }

    private void storePassthroughNodes(AgentCallbackContext ctx, Map<String, Object> toolResult) {
        storePassthroughNodes(conversationId(ctx), toolResult);
    }

    private void storePassthroughNodes(String conversationId, Map<String, Object> toolResult) {
        if (toolResult == null) {
            return;
        }
        Object nodes = toolResult.get("passthrough_nodes");
        if (!(nodes instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<String> normalized = new ArrayList<>();
        for (Object node : list) {
            if (node != null && !String.valueOf(node).isBlank()) {
                normalized.add(String.valueOf(node));
            }
        }
        passthroughBuffer.addAll(conversationId, normalized);
        LOGGER.info("VersatileInterruptRail: queued passthrough nodes conversationId={} count={}",
                conversationId, normalized.size());
    }

    private boolean isInputRequired(Map<String, Object> toolResult) {
        return toolResult != null && "input_required".equals(String.valueOf(toolResult.get("status")));
    }

    private ToolInterruptException inputRequiredInterrupt(AgentCallbackContext ctx, ToolCallInputs inputs,
            Map<String, Object> toolResult) {
        String toolCallId = inputs.getToolCall() != null && inputs.getToolCall().getId() != null
                ? inputs.getToolCall().getId() : "call_versatile";
        // 记录 interruptId，供续传完成时包装 InteractiveInput 恢复 call_versatile。
        passthroughBuffer.rememberInterruptId(conversationId(ctx), toolCallId);
        LOGGER.info("VersatileInterruptRail: adapter requested user input, toolCallId={}", toolCallId);
        InterruptRequest request = InterruptRequest.builder()
                .interruptId(toolCallId)
                .message("")
                .context(Map.of("tool", "call_versatile", "result", toolResult))
                .payloadSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string"),
                                "menu_type", Map.of("type", "string"),
                                "menu_confirm", Map.of("type", "boolean"))))
                .build();
        return new ToolInterruptException(request, inputs.getToolCall());
    }

    private String conversationId(AgentCallbackContext ctx) {
        return ctx.getSession() != null && ctx.getSession().getSessionId() != null
                ? ctx.getSession().getSessionId() : "call-versatile-spike";
    }

    private String extractA2aState(JsonNode result, String fallback) {
        String state = result.path("statusUpdate").path("status").path("state").asText("");
        if (state.isBlank()) {
            state = result.path("status").path("state").asText("");
        }
        return state.isBlank() ? fallback : state;
    }

    private String extractA2aArtifactText(JsonNode result) {
        JsonNode parts = result.path("artifactUpdate").path("artifact").path("parts");
        return extractPartsText(parts);
    }

    private String extractA2aStatusText(JsonNode result) {
        JsonNode parts = result.path("statusUpdate").path("status").path("message").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            parts = result.path("status").path("message").path("parts");
        }
        return extractPartsText(parts);
    }

    private String extractPartsText(JsonNode parts) {
        if (!parts.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : parts) {
            String text = part.path("text").asText("");
            if (text.isBlank()) {
                text = part.path("content").asText("");
            }
            if (!text.isBlank()) {
                builder.append(text);
            }
        }
        return builder.toString();
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

    private Map<String, Object> buildInputs(Map<String, Object> args, AgentCallbackContext ctx) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        ToolDataKey channelKey = ToolDataKeyFactory.fromContext(ctx, edpConfig);
        String query = String.valueOf(args.getOrDefault("query_description", ""));
        if (query.isBlank()) {
            query = readCachedQuery(channelKey);
        }
        inputs.put("query", query);
        inputs.putAll(args);
        inputs.put("query_description", query);

        String inputKey = String.valueOf(args.getOrDefault("input_key", ""));
        if (!inputKey.isBlank()) {
            Object inputData = toolDataChannel.getObject(channelKey, inputKey);
            if (inputData != null) {
                inputs.put("input_data", inputData);
                inputs.put("business_data", inputData);
                LOGGER.info("VersatileInterruptRail: ToolDataChannel hit key={}, input_key={}", channelKey, inputKey);
            } else {
                LOGGER.warn("VersatileInterruptRail: ToolDataChannel miss key={}, input_key={}", channelKey, inputKey);
                inputs.put("input_data", Map.of());
                inputs.put("business_data", Map.of());
            }
        }
        return inputs;
    }

    private String readCachedQuery(ToolDataKey channelKey) {
        Object cached = toolDataChannel.getObject(channelKey, McpInterruptRail.VERSATILE_QUERY_KEY);
        if (cached instanceof String text) {
            return text;
        }
        if (cached instanceof Map<?, ?> map) {
            Object value = map.get("query_description");
            if (value == null) {
                value = map.get("query");
            }
            return value != null ? String.valueOf(value) : "";
        }
        return "";
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

    /**
     * 会话级 Versatile USER 透传缓冲。
     *
     * <p>Rail 在 adapter 响应解析阶段写入完整 message JSON；
     * {@link com.huawei.ascend.edp.handler.EdpaRuntimeHandler} 的流式迭代器
     * 在 DeepAgent 帧之间按 FIFO 刷出，避免 node_type/menu_type 等字段被降维丢失。</p>
     */
    public static final class VersatilePassthroughBuffer {

        private final Map<String, Deque<String>> nodesByConversation = new HashMap<>();
        /** 中断时的 toolCallId，续传完成后用于构造 InteractiveInput。 */
        private final Map<String, String> interruptIdsByConversation = new HashMap<>();

        public void addAll(String conversationId, Collection<String> nodes) {
            if (conversationId == null || conversationId.isBlank() || nodes == null || nodes.isEmpty()) {
                return;
            }
            synchronized (nodesByConversation) {
                Deque<String> queue = nodesByConversation.computeIfAbsent(conversationId, ignored -> new ArrayDeque<>());
                for (String node : nodes) {
                    if (node != null && !node.isBlank()) {
                        queue.addLast(node);
                    }
                }
            }
        }

        public String poll(String conversationId) {
            if (conversationId == null || conversationId.isBlank()) {
                return null;
            }
            synchronized (nodesByConversation) {
                Deque<String> queue = nodesByConversation.get(conversationId);
                if (queue == null) {
                    return null;
                }
                String node = queue.pollFirst();
                if (queue.isEmpty()) {
                    nodesByConversation.remove(conversationId);
                }
                return node;
            }
        }

        public boolean hasPending(String conversationId) {
            if (conversationId == null || conversationId.isBlank()) {
                return false;
            }
            synchronized (nodesByConversation) {
                Deque<String> queue = nodesByConversation.get(conversationId);
                return queue != null && !queue.isEmpty();
            }
        }

        public void clear(String conversationId) {
            if (conversationId != null && !conversationId.isBlank()) {
                synchronized (nodesByConversation) {
                    nodesByConversation.remove(conversationId);
                }
            }
        }

        public void rememberInterruptId(String conversationId, String interruptId) {
            if (conversationId == null || conversationId.isBlank()
                    || interruptId == null || interruptId.isBlank()) {
                return;
            }
            synchronized (interruptIdsByConversation) {
                interruptIdsByConversation.put(conversationId, interruptId);
            }
        }

        public String pollInterruptId(String conversationId) {
            if (conversationId == null || conversationId.isBlank()) {
                return null;
            }
            synchronized (interruptIdsByConversation) {
                return interruptIdsByConversation.remove(conversationId);
            }
        }
    }
}
