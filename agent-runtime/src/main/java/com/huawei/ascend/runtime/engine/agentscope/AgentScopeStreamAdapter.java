package com.huawei.ascend.runtime.engine.agentscope;

import com.huawei.ascend.runtime.engine.InterruptType;
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
            case INTERRUPTED -> AgentExecutionResult.interrupted(InterruptType.HUMAN_INPUT, event.text());
        };
    }

    private AgentExecutionResult mapMap(Map<?, ?> map) {
        String status = firstText(map, "status", "type", "event", "object");
        String text = firstText(map, "text", "output", "content", "delta");
        String error = firstText(map, "error", "error_message", "message");
        if (contains(status, "error") || map.containsKey("error")) {
            return AgentExecutionResult.failed(firstText(map, "error_code", "code"), error);
        }
        if (contains(status, "interrupt") || contains(status, "input_required") || contains(status, "human")) {
            return AgentExecutionResult.interrupted(InterruptType.HUMAN_INPUT, text);
        }
        if (contains(status, "completed") || contains(status, "final")) {
            return AgentExecutionResult.completed(text);
        }
        return AgentExecutionResult.output(text);
    }

    private static boolean contains(String value, String expected) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(expected);
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
