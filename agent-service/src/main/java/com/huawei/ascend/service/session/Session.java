package com.huawei.ascend.service.session;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Session data-context entity.
 *
 * <p>The Session is the data-context layer in the Run ≤ Task ≤ Session ≤
 * Memory lifecycle hierarchy: what was discussed, variables.
 *
 * <p>Decoupled from TaskID: one Session may host many Tasks (per
 * ADR-0100 §decision). Tenant isolation enforced at the storage layer
 * via Postgres RLS per Rule R-J.a (Flyway migration ships in rc25).
 *
 * <p><b>Vocabulary Glossary.</b> Authority: ADR-0136 + ADR-0100 + ADR-0135.
 * Academic prose using "SessionManager" refers to this entity + its companion
 * {@link com.huawei.ascend.service.session.spi.ContextProjector} SPI. AgentSession
 * is NOT a separate SPI — it is a {@code (tenantId, conversationId)}
 * projection over the Run sequence + this {@code Session} data context. The L2
 * "Boundary Contract" commits this entity's input/output and DFX shape; the L1
 * 4+1 rewrite at
 * {@code docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md}
 * is the canonical L1 documentation.
 *
 * @param sessionId  unique session identifier.
 * @param tenantId   mandatory per Rule R-C.c.
 * @param messages   ordered conversation history.
 * @param variables  shared variable bag.
 * @param createdAt  creation timestamp.
 * @param updatedAt  last-update timestamp.
 */
public record Session(
        String sessionId,
        String tenantId,
        List<Map<String, Object>> messages,
        Map<String, Object> variables,
        Instant createdAt,
        Instant updatedAt) {

    public Session {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(variables, "variables");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        // defensive immutable copies so callers
        // can't mutate the record's internal state after construction.
        messages = List.copyOf(messages);
        variables = Map.copyOf(variables);
    }
}
