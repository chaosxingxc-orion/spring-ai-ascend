package com.huawei.ascend.a2a.memory.auth;

/**
 * The authenticated principal a memory operation runs as — the memory layer's OWN
 * identity type (no agent-runtime types). It is the authority for access control:
 *
 * <ul>
 *   <li>{@code tenantId} — the isolation boundary (the engine enforces tenant isolation);</li>
 *   <li>{@code userId} — the authority for PRIVATE (per-user) memory; an agent acts
 *       on the user's behalf, it is not itself the authority;</li>
 *   <li>{@code agentId} — a sub-actor label: who owns a shared-blackboard key it writes.</li>
 * </ul>
 *
 * <p><b>Trust contract.</b> This principal MUST be established at an authenticated
 * boundary (edge / gateway) and forwarded inward; the agent-runtime is only a
 * <i>bearer</i> that carries it, never the authority. A caller-supplied principal
 * that was not authenticated upstream is routing metadata, not a trust boundary —
 * the backend still enforces tenant isolation and ownership server-side. The
 * memory module deliberately does NOT depend on agent-runtime to obtain identity;
 * mapping a runtime execution context to a {@code MemoryPrincipal} is the caller's
 * (or a thin adapter's) job.
 */
public record MemoryPrincipal(String tenantId, String userId, String agentId) {

    public MemoryPrincipal {
        tenantId = tenantId == null || tenantId.isBlank() ? "default" : tenantId;
        userId = userId == null ? "" : userId;
        agentId = agentId == null || agentId.isBlank() ? "anonymous-agent" : agentId;
    }

    /** An agent acting in a collaboration (no specific end-user). */
    public static MemoryPrincipal agent(String tenantId, String agentId) {
        return new MemoryPrincipal(tenantId, "", agentId);
    }

    /** An agent acting on a specific user's behalf. */
    public static MemoryPrincipal forUser(String tenantId, String userId, String agentId) {
        return new MemoryPrincipal(tenantId, userId, agentId);
    }
}
