package com.huawei.ascend.runtime.engine.agentscope;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class AgentScopeStreamAdapter implements StreamAdapter {

    @Override
    public Stream<AgentExecutionResult> adapt(Stream<?> rawResults) {
        return rawResults.map(this::map);
    }

    public AgentExecutionResult map(Object rawResult) {
        if (rawResult instanceof AgentScopeEvent event) {
            return mapEvent(event);
        }
        if (rawResult instanceof Map<?, ?> map) {
            return mapMap(map);
        }
        return AgentExecutionResult.completed(rawResult == null ? "" : String.valueOf(rawResult));
    }

    private AgentExecutionResult mapEvent(AgentScopeEvent event) {
        return switch (event.type()) {
            case OUTPUT -> AgentExecutionResult.output(event.text());
            case COMPLETED -> AgentExecutionResult.completed(event.text());
            case FAILED -> AgentExecutionResult.failed(event.errorCode(), event.errorMessage());
            case INTERRUPTED -> AgentExecutionResult.interrupted(event.text());
        };
    }

    private AgentExecutionResult mapMap(Map<?, ?> map) {
        String status = firstText(map, "status", "type", "event", "object");
        String text = textValue(firstNonNull(map, "text", "output", "content", "delta"));
        Object error = firstNonNull(map, "error", "error_message");
        String explicitError = errorText(error);
        if (isFailureStatus(status) || !explicitError.isBlank()) {
            String errorMessage = !explicitError.isBlank() ? explicitError : firstText(map, "message");
            return AgentExecutionResult.failed(errorCode(map, error), errorMessage);
        }
        if (isInterruptStatus(status)) {
            return AgentExecutionResult.interrupted(text);
        }
        if (isCompletedStatus(status) && !isMessageLevelEvent(map)) {
            return AgentExecutionResult.completed(text);
        }
        return AgentExecutionResult.output(text);
    }

    /**
     * The AgentScope runtime streams per-message events ({@code object:
     * "message"/"content"}) whose {@code status} flips to {@code completed}
     * when that single message finishes; only the run-level event ({@code
     * object: "response"} or flat events without an object marker) may
     * complete the whole execution.
     */
    private static boolean isMessageLevelEvent(Map<?, ?> map) {
        String object = normalizeStatus(firstText(map, "object"));
        return "message".equals(object) || "content".equals(object);
    }

    /** Extracts human text from string, content-list, and message-map event payloads. */
    private static String textValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof List<?> items) {
            StringBuilder text = new StringBuilder();
            for (Object item : items) {
                text.append(textValue(item));
            }
            return text.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return textValue(firstNonNull(map, "text", "content", "output", "delta"));
        }
        return String.valueOf(value);
    }

    private static String errorText(Object error) {
        if (error == null || Boolean.FALSE.equals(error)) {
            return "";
        }
        if (error instanceof Map<?, ?> map) {
            String message = textValue(firstNonNull(map, "message", "error_message"));
            return message.isBlank() ? String.valueOf(map) : message;
        }
        return String.valueOf(error);
    }

    private static String errorCode(Map<?, ?> map, Object error) {
        String code = firstText(map, "error_code", "code");
        if (!code.isBlank() || !(error instanceof Map<?, ?> errorMap)) {
            return code;
        }
        return firstText(errorMap, "error_code", "code");
    }

    private static Object firstNonNull(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean isFailureStatus(String value) {
        String status = normalizeStatus(value);
        return switch (status) {
            case "error", "errored", "failed", "failure", "exception" -> true;
            default -> false;
        };
    }

    private static boolean isInterruptStatus(String value) {
        String status = normalizeStatus(value);
        return switch (status) {
            case "interrupt", "interrupted", "input_required", "requires_input", "human", "human_input" -> true;
            default -> false;
        };
    }

    private static boolean isCompletedStatus(String value) {
        String status = normalizeStatus(value);
        return switch (status) {
            case "completed", "complete", "final", "finished", "done", "success", "succeeded" -> true;
            default -> false;
        };
    }

    private static String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String firstText(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }
}
