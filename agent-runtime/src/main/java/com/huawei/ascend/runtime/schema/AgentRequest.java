package com.huawei.ascend.service.schema;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical inbound run request, the standard object every access protocol
 * adapter converts to before entering the service.
 *
 * <p>Aligned with the agentscope-runtime {@code AgentRequest} (input messages +
 * session/user identity), extended with the multi-tenant and agent-routing
 * identity this service needs ({@code tenantId}, {@code agentId}). Protocol
 * adapters (A2A today, MQ later) own the mapping from their wire envelope into
 * this type, so downstream layers never see protocol specifics.
 *
 * @param tenantId       owning tenant; never blank.
 * @param userId         end user id; may be {@code null} when anonymous.
 * @param agentId        target agent id; never blank.
 * @param sessionId      conversation/session id; may be {@code null} before
 *                       access resolves the request through SessionManager.
 * @param input          ordered input messages; never {@code null}, may be empty.
 * @param idempotencyKey optional client-supplied dedup key; may be {@code null}.
 * @param metadata       protocol/routing attributes (e.g. correlationId,
 *                       streaming flags); never {@code null}.
 */
public record AgentRequest(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        List<Message> input,
        String idempotencyKey,
        Map<String, Object> metadata) {

    public AgentRequest {
        tenantId = requireNonBlank(tenantId, "tenantId");
        agentId = requireNonBlank(agentId, "agentId");
        sessionId = blankToNull(sessionId);
        input = input == null ? List.of() : List.copyOf(input);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** The most recent user text in {@link #input}, or empty when none. */
    public String latestUserText() {
        for (int i = input.size() - 1; i >= 0; i--) {
            Message message = input.get(i);
            if (message.role() == Role.USER) {
                return message.text();
            }
        }
        return input.isEmpty() ? "" : input.get(input.size() - 1).text();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
