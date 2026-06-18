package com.huawei.ascend.a2a.memory.a2a;

import com.huawei.ascend.a2a.memory.auth.MemoryPrincipal;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;

/**
 * Binds the shared blackboard to an agent acting in one A2A collaboration. The
 * authority is the {@link MemoryPrincipal} the caller supplies — established at an
 * authenticated boundary and forwarded inward (the agent-runtime is only a bearer).
 * This module does NOT depend on agent-runtime: mapping a runtime execution
 * context to a {@code MemoryPrincipal} + collaborationId (the A2A contextId) is the
 * caller's job, so memory authority never lives in the runtime.
 *
 * <pre>{@code
 * // caller forwards an authenticated principal + the A2A contextId:
 * var board = A2aSharedMemory.forCollaboration(principal, contextId, store);
 * board.put("riskAssessment", json);   // owned by principal.agentId()
 * board.get("loanDecision");           // read another agent's conclusion (recorded)
 * }</pre>
 */
public final class A2aSharedMemory {

    private A2aSharedMemory() {
    }

    /**
     * @param principal       authenticated principal (tenant + user + agent); the access authority
     * @param collaborationId the collaboration root (the A2A contextId) — keys this blackboard
     */
    public static A2aSharedMemoryHandle forCollaboration(MemoryPrincipal principal, String collaborationId,
            SharedMemoryStore store) {
        return forCollaboration(principal, collaborationId, store, MemoryObserver.NOOP);
    }

    public static A2aSharedMemoryHandle forCollaboration(MemoryPrincipal principal, String collaborationId,
            SharedMemoryStore store, MemoryObserver observer) {
        return new A2aSharedMemoryHandle(store, principal.tenantId(), collaborationId, principal.agentId(), observer);
    }
}
