package com.huawei.ascend.examples.a2a;

import com.huawei.ascend.runtime.engine.a2a.Messages;
import io.agentscope.core.message.MsgRole;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Shared wire-role and text mapping for the sample AgentScope runtime endpoints.
 *
 * The A2A {@link Message.Role} enum only carries user/agent, so the original
 * wire role ("system", "tool", ...) is preserved in message metadata and
 * restored when the message is handed to the AgentScope SDK.
 */
final class AgentScopeWireMessages {

    static final String WIRE_ROLE_METADATA_KEY = "wireRole";

    private AgentScopeWireMessages() {
    }

    static Message message(Object wireRole, String text) {
        String normalized = normalize(wireRole);
        Message.Builder builder = Message.builder()
                .role(toA2aRole(normalized))
                .parts(List.of(new TextPart(text)));
        if (!normalized.isEmpty()) {
            builder.metadata(Map.of(WIRE_ROLE_METADATA_KEY, normalized));
        }
        return builder.build();
    }

    static Message.Role toA2aRole(Object wireRole) {
        String normalized = normalize(wireRole);
        return "assistant".equals(normalized) || "agent".equals(normalized)
                ? Message.Role.ROLE_AGENT
                : Message.Role.ROLE_USER;
    }

    static MsgRole toMsgRole(Message message) {
        Map<String, Object> metadata = message.metadata();
        String wireRole = metadata == null ? "" : normalize(metadata.get(WIRE_ROLE_METADATA_KEY));
        return switch (wireRole) {
            case "system" -> MsgRole.SYSTEM;
            case "tool" -> MsgRole.TOOL;
            case "assistant", "agent" -> MsgRole.ASSISTANT;
            default -> message.role() == Message.Role.ROLE_AGENT ? MsgRole.ASSISTANT : MsgRole.USER;
        };
    }

    static String text(Message message) {
        return Messages.text(message);
    }

    private static String normalize(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
    }
}
