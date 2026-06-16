package com.huawei.ascend.a2a.memory.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Run-scoped blackboard: ownership write model, hand-over immutability, append-log + provenance. */
class SharedMemoryKitTest {

    private static SharedMemoryKit board() {
        return SharedMemoryKit.forCollaboration(
                new InMemorySharedMemoryStore(() -> 1_000L), "demo-tenant", "collab-1");
    }

    @Test
    void ownerCanUpdateItsOwnKey() {
        SharedMemoryKit b = board();
        b.put("riskAssessment", "v1", "risk-agent");
        b.put("riskAssessment", "v2", "risk-agent");
        assertEquals("v2", b.get("riskAssessment").orElseThrow(), "owner re-write returns latest");
        assertEquals(2, b.history("riskAssessment").size(), "appended, not overwritten");
    }

    @Test
    void nonOwnerCannotWriteAnotherAgentsKey() {
        SharedMemoryKit b = board();
        b.put("riskAssessment", "high", "risk-agent");
        OwnershipViolationException ex = assertThrows(OwnershipViolationException.class,
                () -> b.put("riskAssessment", "low", "marketing-agent"));
        assertTrue(ex.getMessage().contains("risk-agent"), "names the owner");
        assertEquals("high", b.get("riskAssessment").orElseThrow(), "value unchanged after rejected write");
    }

    @Test
    void afterHandoverBWritesItsOwnKeysAndAsStayReadOnly() {
        SharedMemoryKit b = board();
        // agent A concludes
        b.put("riskAssessment", "C3", "risk-agent");
        // task hands over to B; B writes its OWN key
        b.put("loanDecision", "approved", "loan-agent");
        assertEquals("approved", b.get("loanDecision").orElseThrow());
        // B cannot mutate A's conclusion
        assertThrows(OwnershipViolationException.class,
                () -> b.put("riskAssessment", "C1", "loan-agent"));
    }

    @Test
    void appendLogCarriesProvenanceAndVersionOrder() {
        SharedMemoryKit b = board();
        b.put("k", "a", "agent-x");
        b.put("k", "b", "agent-x");
        List<SharedEntry> history = b.history("k");
        assertEquals(2, history.size());
        assertEquals(1, history.get(0).version());
        assertEquals(2, history.get(1).version());
        assertEquals("agent-x", history.get(0).writerAgentId(), "provenance recorded");
        assertEquals(1_000L, history.get(0).tsEpochMs());
    }

    @Test
    void keysAndReleaseLifecycle() {
        SharedMemoryKit b = board();
        b.put("k1", "v", "a");
        b.put("k2", "v", "a");
        assertEquals(2, b.keys().size());
        b.release();
        assertTrue(b.keys().isEmpty(), "released blackboard is empty");
        assertFalse(b.get("k1").isPresent());
    }
}
