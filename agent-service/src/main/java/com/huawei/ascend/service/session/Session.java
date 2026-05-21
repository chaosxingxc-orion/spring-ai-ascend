package com.huawei.ascend.service.session;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Session data-context entity per ADR-0100 (rc25).
 *
 * <p>The Session is the data-context layer in the Run ≤ Task ≤ Session ≤
 * Memory lifecycle hierarchy: what was discussed, variables.
 *
 * <p>Decoupled from TaskID: one Session may host many Tasks (per
 * ADR-0100 §decision). Tenant isolation enforced at the storage layer
 * via Postgres RLS per Rule R-J.a (Flyway migration ships in rc25).
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
    }
}
