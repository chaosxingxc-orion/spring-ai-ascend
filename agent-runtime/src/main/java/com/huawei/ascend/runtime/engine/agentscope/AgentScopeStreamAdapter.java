package com.huawei.ascend.runtime.engine.agentscope;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
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
        String text = firstText(map, "text", "output", "content", "delta");
        String explicitError = firstText(map, "error", "error_message");
        String errorMessage = !explicitError.isBlank() ? explicitError : firstText(map, "message");
        if (isFailureStatus(status) || !explicitError.isBlank()) {
            return AgentExecutionResult.failed(firstText(map, "error_code", "code"), errorMessage);
        }
        if (isInterruptStatus(status)) {
            return AgentExecutionResult.interrupted(text);
        }
        if (isCompletedStatus(status)) {
            return AgentExecutionResult.completed(text);
        }
        return AgentExecutionResult.output(text);
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
