package com.huawei.ascend.runtime.engine.agentscope;

import java.util.Map;

/**
 * AgentScope SDK/runtime event flattened to the current agent-runtime result model.
 */
public record AgentScopeEvent(
        Type type,
        String text,
        String errorCode,
        String errorMessage,
        Map<String, Object> metadata) {

    public enum Type {
        OUTPUT,
        COMPLETED,
        FAILED,
        INTERRUPTED
    }

    public AgentScopeEvent {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        text = text == null ? "" : text;
        errorCode = errorCode == null || errorCode.isBlank() ? "AGENTSCOPE_ERROR" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentScopeEvent output(String text) {
        return new AgentScopeEvent(Type.OUTPUT, text, null, null, Map.of());
    }

    public static AgentScopeEvent completed(String text) {
        return new AgentScopeEvent(Type.COMPLETED, text, null, null, Map.of());
    }

    public static AgentScopeEvent failed(String code, String message) {
        return new AgentScopeEvent(Type.FAILED, "", code, message, Map.of());
    }

    public static AgentScopeEvent interrupted(String prompt) {
        return new AgentScopeEvent(Type.INTERRUPTED, prompt, null, null, Map.of());
    }
}
