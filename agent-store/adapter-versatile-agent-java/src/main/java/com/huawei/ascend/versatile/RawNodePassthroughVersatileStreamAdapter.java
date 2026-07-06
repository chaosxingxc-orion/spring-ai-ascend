package com.huawei.ascend.versatile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.versatile.VersatileProperties;
import com.huawei.ascend.runtime.engine.versatile.VersatileStreamAdapter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Versatile 节点保真透传适配器。
 *
 * <p>除配置的结果节点外，所有 Versatile {@code message.data} 节点都以完整
 * {@code {"event":"message","data":{...}}} JSON 文本透传给上游 USER，避免框架默认
 * 只输出 {@code text/summary} 导致 {@code node_type/node_name/menu_type} 等字段丢失。</p>
 */
public class RawNodePassthroughVersatileStreamAdapter extends VersatileStreamAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(RawNodePassthroughVersatileStreamAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String resultNodeType;
    private final String resultNodeName;

    public RawNodePassthroughVersatileStreamAdapter(VersatileProperties properties, String resultNodeName) {
        super(properties);
        this.resultNodeType = properties != null ? trimToEmpty(properties.getResultNodeType()) : "";
        this.resultNodeName = trimToEmpty(resultNodeName);
    }

    @Override
    public Stream<AgentExecutionResult> adapt(Stream<?> rawResults) {
        final Iterator<?> iterator = rawResults.iterator();
        final List<String> resultTexts = new ArrayList<>();
        final boolean[] hasEnd = {false};
        final boolean[] done = {false};

        return Stream.generate(() -> {
            if (done[0]) {
                return null;
            }
            while (iterator.hasNext()) {
                AgentExecutionResult result = mapLine(iterator.next(), resultTexts, hasEnd);
                if (result != null) {
                    if (isTerminal(result)) {
                        done[0] = true;
                    }
                    return result;
                }
            }
            return null;
        }).takeWhile(Objects::nonNull);
    }

    @SuppressWarnings("unchecked")
    private AgentExecutionResult mapLine(Object raw, List<String> resultTexts, boolean[] hasEnd) {
        if (raw == null) {
            return null;
        }
        String line = String.valueOf(raw).trim();
        if (line.isEmpty()) {
            return null;
        }
        String jsonText = stripDataPrefix(line);
        if (jsonText.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> frame = MAPPER.readValue(jsonText, MAP_TYPE);
            String event = string(frame.get("event"));
            if (event.isBlank() && looksLikeRawNode(frame)) {
                // 部分 mock/网关只推送裸 node 字段，补 event/data 包装以保持透传格式一致。
                Map<String, Object> wrapped = new java.util.LinkedHashMap<>();
                wrapped.put("event", "message");
                wrapped.put("data", frame);
                frame = wrapped;
                event = "message";
            } else if (event.isBlank()) {
                return null;
            }
            Object dataObject = frame.get("data");
            Map<String, Object> data = dataObject instanceof Map<?, ?> map
                    ? (Map<String, Object>) map : Map.of();
            return switch (event) {
                case "message" -> handleMessage(frame, data, resultTexts, hasEnd);
                case "workflow_finished" -> complete(resultTexts, workflowContent(data));
                case "exception" -> AgentExecutionResult.failed(
                        prefixCode(string(data.get("code"))), string(data.get("message")));
                case "end" -> complete(resultTexts, "");
                // 断连但未收到 End 节点：视为用户中断而非终态完成，避免把半帧当 LLM 结果回灌。
                case "connection_closed" -> hasEnd[0]
                        ? complete(resultTexts, "")
                        : AgentExecutionResult.interrupted("", AgentExecutionResult.Target.USER);
                case "workflow_started", "node_started", "node_finished" -> null;
                default -> AgentExecutionResult.output(jsonText, AgentExecutionResult.Target.USER);
            };
        } catch (Exception e) {
            LOG.warn("versatile raw node passthrough parse skipped: {}",
                    line.length() > 120 ? line.substring(0, 120) + "..." : line);
            return null;
        }
    }

    private AgentExecutionResult handleMessage(Map<String, Object> frame, Map<String, Object> data,
            List<String> resultTexts, boolean[] hasEnd) throws Exception {
        String nodeType = string(data.get("node_type"));
        String nodeName = string(data.get("node_name"));
        if ("End".equalsIgnoreCase(nodeType)) {
            hasEnd[0] = true;
        }
        if (isResultNode(nodeType, nodeName)) {
            String text = messageText(data);
            if (!text.isBlank()) {
                resultTexts.add(text);
            }
            // 结果节点只参与终态 LLM 汇总，不向前端重复透传。
            LOG.info("versatile result node suppressed node_type={} node_name={}", nodeType, nodeName);
            return null;
        }
        return AgentExecutionResult.output(MAPPER.writeValueAsString(frame), AgentExecutionResult.Target.USER);
    }

    private boolean isResultNode(String nodeType, String nodeName) {
        return !resultNodeType.isBlank()
                && !resultNodeName.isBlank()
                && resultNodeType.equalsIgnoreCase(nodeType)
                && resultNodeName.equals(nodeName);
    }

    private AgentExecutionResult complete(List<String> resultTexts, String extraContent) {
        StringBuilder content = new StringBuilder();
        for (String text : resultTexts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            if (!content.isEmpty()) {
                content.append('\n');
            }
            content.append(text);
        }
        if (extraContent != null && !extraContent.isBlank()) {
            if (!content.isEmpty()) {
                content.append('\n');
            }
            content.append(extraContent);
        }
        return AgentExecutionResult.completed(content.toString(), AgentExecutionResult.Target.LLM);
    }

    private static boolean isTerminal(AgentExecutionResult result) {
        return result.type() == AgentExecutionResult.Type.COMPLETED
                || result.type() == AgentExecutionResult.Type.FAILED
                || result.type() == AgentExecutionResult.Type.INTERRUPTED;
    }

    private static String stripDataPrefix(String line) {
        return line.startsWith("data:") ? line.substring(5).trim() : line;
    }

    private static String messageText(Map<String, Object> data) {
        String text = string(data.get("text"));
        if (!text.isBlank()) {
            return text;
        }
        return string(data.get("summary"));
    }

    @SuppressWarnings("unchecked")
    private static String workflowContent(Map<String, Object> data) {
        Object outputsObject = data.get("outputs");
        if (outputsObject instanceof Map<?, ?> outputs) {
            return string(((Map<String, Object>) outputs).get("responseContent"));
        }
        return "";
    }

    private static boolean looksLikeRawNode(Map<String, Object> frame) {
        return frame.containsKey("node_type") || frame.containsKey("node_name") || frame.containsKey("menu_type");
    }

    private static String prefixCode(String code) {
        return code.isBlank() ? "VERSATILE_UNKNOWN" : "VERSATILE_" + code;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
