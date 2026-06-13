package com.huawei.ascend.runtime.engine.agentscope;

import com.huawei.ascend.runtime.common.RuntimeMessage;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Invocation handed to AgentScope SDK or runtime-client bridges. Carries only
 * runtime-native types ({@link RuntimeMessage} as the message model), so
 * AgentScope-side consumers never depend on a wire protocol.
 */
public record AgentScopeInvocation(
        String tenantId,
        String userId,
        String sessionId,
        String taskId,
        String agentId,
        String inputType,
        List<RuntimeMessage> messages,
        Map<String, Object> variables,
        Map<String, Object> metadata) {

    public AgentScopeInvocation {
        tenantId = requireNonBlank(tenantId, "tenantId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        taskId = requireNonBlank(taskId, "taskId");
        agentId = requireNonBlank(agentId, "agentId");
        inputType = inputType == null || inputType.isBlank() ? "USER_MESSAGE" : inputType;
        messages = messages == null ? List.of() : List.copyOf(messages);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
