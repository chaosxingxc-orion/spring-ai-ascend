package com.huawei.ascend.runtime.engine.langgraph;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Maps LangGraph SSE events to neutral execution results. {@code values}
 * events are full state snapshots, so the adapter tracks the assistant text
 * seen so far and emits only the newly appended suffix as OUTPUT; {@code end}
 * completes the run; {@code error} fails it. LangServe's {@code data}/{@code
 * end} dialect and bare un-named frames are handled by the same rules.
 *
 * <p>State lives in the per-{@code adapt} mapper, never on this adapter —
 * one shared adapter instance serves concurrent executions.
 */
public final class LangGraphStreamAdapter implements StreamAdapter {

    @Override
    public Stream<AgentExecutionResult> adapt(Stream<?> rawResults) {
        EventMapper mapper = new EventMapper();
        return rawResults.flatMap(mapper::map);
    }

    private static final class EventMapper {
        private String assistantText = "";

        Stream<AgentExecutionResult> map(Object rawResult) {
            if (!(rawResult instanceof Map<?, ?> map)) {
                return rawResult == null
                        ? Stream.empty()
                        : Stream.of(AgentExecutionResult.output(String.valueOf(rawResult)));
            }
            String event = normalize(map.get(LangGraphRuntimeClient.EVENT_KEY));
            Object data = map.containsKey(LangGraphRuntimeClient.DATA_KEY)
                    ? map.get(LangGraphRuntimeClient.DATA_KEY)
                    : map;
            if (isErrorEvent(event, data)) {
                return Stream.of(failure(data));
            }
            if ("end".equals(event)) {
                return Stream.of(AgentExecutionResult.completed(""));
            }
            if ("metadata".equals(event) || event.startsWith("updates")) {
                return Stream.empty();
            }
            // values snapshots, messages/partial chunk lists, LangServe data
            // chunks, and bare frames all reduce to "the assistant text so far".
            String text = assistantText(data);
            if (text.isBlank()) {
                return Stream.empty();
            }
            String delta = text.startsWith(assistantText) ? text.substring(assistantText.length()) : text;
            if (text.length() >= assistantText.length()) {
                assistantText = text;
            }
            return delta.isBlank() ? Stream.empty() : Stream.of(AgentExecutionResult.output(delta));
        }

        private static boolean isErrorEvent(String event, Object data) {
            if ("error".equals(event)) {
                return true;
            }
            return data instanceof Map<?, ?> map && map.get("error") != null
                    && !Boolean.FALSE.equals(map.get("error"));
        }

        private static AgentExecutionResult failure(Object data) {
            if (!(data instanceof Map<?, ?> map)) {
                return AgentExecutionResult.failed("LANGGRAPH_RUNTIME_ERROR",
                        data == null ? "" : String.valueOf(data));
            }
            String code = text(map.get("error"));
            String message = text(map.get("message"));
            if (message.isBlank()) {
                message = code;
            }
            return AgentExecutionResult.failed(code.isBlank() ? "LANGGRAPH_RUNTIME_ERROR" : code, message);
        }

        /** The latest assistant text in a values snapshot, message-chunk list, or chunk map. */
        private static String assistantText(Object data) {
            if (data == null) {
                return "";
            }
            if (data instanceof String text) {
                return text;
            }
            if (data instanceof List<?> items) {
                // messages/partial: a list of message chunks — the last one is current.
                for (int i = items.size() - 1; i >= 0; i--) {
                    String text = assistantText(items.get(i));
                    if (!text.isBlank()) {
                        return text;
                    }
                }
                return "";
            }
            if (data instanceof Map<?, ?> map) {
                Object messages = map.get("messages");
                if (messages instanceof List<?> list) {
                    return lastAssistantMessageText(list);
                }
                return contentText(map.get("content"));
            }
            return "";
        }

        private static String lastAssistantMessageText(List<?> messages) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof Map<?, ?> message && isAssistant(message)) {
                    return contentText(message.get("content"));
                }
            }
            return "";
        }

        private static boolean isAssistant(Map<?, ?> message) {
            String type = normalize(message.get("type"));
            String role = normalize(message.get("role"));
            return type.startsWith("ai") || "assistant".equals(role);
        }

        /** LangChain message content is a string or a list of typed parts. */
        private static String contentText(Object content) {
            if (content instanceof String text) {
                return text;
            }
            if (content instanceof List<?> parts) {
                StringBuilder text = new StringBuilder();
                for (Object part : parts) {
                    if (part instanceof String s) {
                        text.append(s);
                    } else if (part instanceof Map<?, ?> partMap) {
                        Object value = partMap.get("text");
                        if (value != null) {
                            text.append(value);
                        }
                    }
                }
                return text.toString();
            }
            return "";
        }

        private static String text(Object value) {
            return value == null ? "" : String.valueOf(value);
        }

        private static String normalize(Object value) {
            return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        }
    }
}
