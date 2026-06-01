package com.huawei.ascend.service.schema;

/**
 * Canonical message role, shared across the agent-service modules.
 *
 * <p>Aligned with the agentscope-runtime role vocabulary
 * ({@code user / assistant / system / tool}) so that access, session,
 * task-control and engine all speak one role type instead of each
 * declaring its own.
 */
public enum Role {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL;

    /**
     * Parses a role from its lower-case wire form ({@code "user"} etc.),
     * tolerating case and surrounding whitespace. Unknown values fall back
     * to {@link #USER} so malformed inbound payloads never break dispatch.
     */
    public static Role fromWire(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        return switch (value.trim().toLowerCase()) {
            case "assistant" -> ASSISTANT;
            case "system" -> SYSTEM;
            case "tool" -> TOOL;
            default -> USER;
        };
    }

    /** The lower-case wire form used by the agent frameworks and protocols. */
    public String wire() {
        return name().toLowerCase();
    }
}
