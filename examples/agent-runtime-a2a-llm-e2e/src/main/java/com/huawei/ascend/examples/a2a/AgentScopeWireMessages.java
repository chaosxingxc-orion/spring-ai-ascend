package com.huawei.ascend.examples.a2a;

import com.huawei.ascend.runtime.common.RuntimeMessage;
import io.agentscope.core.message.MsgRole;
import java.util.Locale;
import java.util.Map;

/**
 * Shared wire-role and text mapping for the sample AgentScope runtime endpoints.
 *
 * The runtime {@link RuntimeMessage.Role} model only carries user/agent, so the
 * original wire role ("system", "tool", ...) is preserved in message metadata
 * and restored when the message is handed to the AgentScope SDK.
 */
final class AgentScopeWireMessages {

    static final String WIRE_ROLE_METADATA_KEY = "wireRole";

    private AgentScopeWireMessages() {
    }

    static RuntimeMessage message(Object wireRole, String text) {
        String normalized = normalize(wireRole);
        Map<String, Object> metadata = normalized.isEmpty()
                ? Map.of()
                : Map.of(WIRE_ROLE_METADATA_KEY, normalized);
        return new RuntimeMessage(toRuntimeRole(normalized), text, metadata);
    }

    static RuntimeMessage.Role toRuntimeRole(Object wireRole) {
        String normalized = normalize(wireRole);
        return "assistant".equals(normalized) || "agent".equals(normalized)
                ? RuntimeMessage.Role.AGENT
                : RuntimeMessage.Role.USER;
    }

    static MsgRole toMsgRole(RuntimeMessage message) {
        String wireRole = normalize(message.metadata().get(WIRE_ROLE_METADATA_KEY));
        return switch (wireRole) {
            case "system" -> MsgRole.SYSTEM;
            case "tool" -> MsgRole.TOOL;
            case "assistant", "agent" -> MsgRole.ASSISTANT;
            default -> message.role() == RuntimeMessage.Role.AGENT ? MsgRole.ASSISTANT : MsgRole.USER;
        };
    }

    private static String normalize(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
    }
}
