package com.huawei.ascend.runtime.common;

import java.util.Map;

/**
 * Protocol-neutral conversation message — the single input carrier the runtime
 * hands to framework adapters and business handlers. The two-role model is the
 * smallest common denominator across wire protocols; richer wire roles
 * ("system", "tool", ...) ride in {@code metadata} so no information is lost
 * when a protocol bridge maps its native message onto this type.
 *
 * @param role     authoring side; defaults to {@link Role#USER} when null
 * @param text     plain text content; never null (empty when absent)
 * @param metadata protocol- or framework-specific extras; never null
 */
public record RuntimeMessage(Role role, String text, Map<String, Object> metadata) {

    public enum Role { USER, AGENT }

    public RuntimeMessage {
        role = role == null ? Role.USER : role;
        text = text == null ? "" : text;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static RuntimeMessage user(String text) {
        return new RuntimeMessage(Role.USER, text, Map.of());
    }

    public static RuntimeMessage agent(String text) {
        return new RuntimeMessage(Role.AGENT, text, Map.of());
    }
}
