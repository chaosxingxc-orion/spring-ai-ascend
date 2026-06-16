package com.huawei.ascend.a2a.memory.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.a2a.memory.auth.MemoryPrincipal;
import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.InteractionEntry.InteractionType;
import com.huawei.ascend.a2a.memory.shared.OwnershipViolationException;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A2A agent-to-agent sharing, authority via {@link MemoryPrincipal} (NOT runtime).
 * The runtime→principal mapping lives HERE, at the caller — the memory module no
 * longer depends on agent-runtime for identity. Two agents in the same
 * collaboration (same A2A contextId) read each other's conclusions; a third in
 * another collaboration sees nothing; ownership holds; reads are attributed.
 */
class A2aSharedMemoryTest {

    private static final String CONTEXT = "collab-7"; // the A2A contextId shared by collaborators

    /** Caller-side mapping: an authenticated runtime context → a MemoryPrincipal + the A2A contextId. */
    private static A2aSharedMemoryHandle board(String agentId, String contextId, SharedMemoryStore store) {
        RuntimeIdentity scope = new RuntimeIdentity("bank", "u-1", contextId, "task-1", agentId);
        AgentExecutionContext ctx = new AgentExecutionContext(scope, "USER_MESSAGE", List.of(), Map.of(), contextId, null);
        RuntimeIdentity s = ctx.getScope();
        MemoryPrincipal principal = MemoryPrincipal.forUser(s.tenantId(), s.userId(), s.agentId());
        return A2aSharedMemory.forCollaboration(principal, s.sessionId(), store);
    }

    @Test
    void agentsInTheSameCollaborationShareConclusions() {
        SharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);
        board("risk-agent", CONTEXT, store).put("riskAssessment", "C3 medium");
        Optional<String> seen = board("advisor-agent", CONTEXT, store).get("riskAssessment");
        assertEquals(Optional.of("C3 medium"), seen, "advisor reads risk-agent's conclusion via the A2A contextId");
    }

    @Test
    void othersReadButOnlyTheOwnerCanChangeAKey() {
        SharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);
        board("risk-agent", CONTEXT, store).put("riskAssessment", "C3");
        A2aSharedMemoryHandle advisor = board("advisor-agent", CONTEXT, store);
        assertTrue(advisor.get("riskAssessment").isPresent(), "advisor may read");
        assertThrows(OwnershipViolationException.class, () -> advisor.put("riskAssessment", "tampered"),
                "a non-owner cannot change another agent's conclusion");
    }

    @Test
    void differentCollaborationIsIsolated() {
        SharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);
        board("risk-agent", CONTEXT, store).put("riskAssessment", "C3");
        Optional<String> otherCollab = board("advisor-agent", "collab-OTHER", store).get("riskAssessment");
        assertTrue(otherCollab.isEmpty(), "a different A2A collaboration shares nothing");
    }

    @Test
    void eachAgentOwnsItsOwnKeysHandoverStyle() {
        SharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);
        board("risk-agent", CONTEXT, store).put("riskAssessment", "C3");
        board("loan-agent", CONTEXT, store).put("loanDecision", "approved");
        A2aSharedMemoryHandle reader = board("advisor-agent", CONTEXT, store);
        assertEquals(Optional.of("C3"), reader.get("riskAssessment"));
        assertEquals(Optional.of("approved"), reader.get("loanDecision"));
        assertEquals(2, reader.keys().size());
    }

    @Test
    void readsAreAttributedIntoTheInteractionRecord() {
        SharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);
        board("risk-agent", CONTEXT, store).put("riskAssessment", "C3");
        // advisor reads risk-agent's conclusion → a READ dependency edge is recorded
        board("advisor-agent", CONTEXT, store).get("riskAssessment");

        var edges = store.interactions("bank", CONTEXT);
        assertTrue(edges.stream().anyMatch(e -> e.type() == InteractionType.READ
                        && e.actorAgentId().equals("advisor-agent")
                        && e.targetAgentId().equals("risk-agent")
                        && e.key().equals("riskAssessment")),
                "the read edge advisor→risk-agent on riskAssessment is in the team record");
    }
}
