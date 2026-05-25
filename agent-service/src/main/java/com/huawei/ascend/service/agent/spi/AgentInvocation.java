package com.huawei.ascend.service.agent.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Synchronous {@link Agent#invoke(AgentInvocation)} request.
 *
 * <p>Authority: ADR-0128.
 *
 * @param tenantId        owning tenant (Rule R-C.c).
 * @param agentId         target agent.
 * @param userMessage     end-user message; never null.
 * @param conversationId  optional conversation grouping for
 *                        episodic memory.
 * @param context         opaque per-invocation context.
 */
public record AgentInvocation(
        String tenantId,
        String agentId,
        String userMessage,
        Optional<String> conversationId,
        Map<String, Object> context) {

    public AgentInvocation {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(userMessage, "userMessage");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(context, "context");
        context = Map.copyOf(context);
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be non-blank (Rule R-C.c)");
        }
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must be non-blank");
        }
    }
}
