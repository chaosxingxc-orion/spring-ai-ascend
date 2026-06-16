package com.huawei.ascend.a2a.memory.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Owner-unavailable handling (Q4): a non-owner cannot mutate a key, but MAY
 * supersede it (privileged) — which appends a new version, transfers ownership,
 * and preserves the prior owner's history; the supersession is recorded.
 */
class SupersedeTest {

    private final InMemorySharedMemoryStore store = new InMemorySharedMemoryStore(() -> 1L);

    @Test
    void nonOwnerMustSupersedeNotMutate() {
        store.append("bank", "c1", "risk", "C3", "risk-agent", null);
        // normal write by another agent is rejected
        assertThrows(OwnershipViolationException.class,
                () -> store.append("bank", "c1", "risk", "C2", "backup-agent", null));
        // but supersede (owner unavailable) is allowed
        SharedEntry s = store.supersede("bank", "c1", "risk", "C2", "backup-agent", "risk-agent unavailable");
        assertEquals("C2", s.value());
        assertEquals("backup-agent", s.writerAgentId());
        assertEquals("C2", store.latest("bank", "c1", "risk").orElseThrow().value(), "latest is the superseding value");
    }

    @Test
    void priorOwnersHistoryIsPreserved() {
        store.append("bank", "c1", "risk", "C3", "risk-agent", null);
        store.supersede("bank", "c1", "risk", "C2", "backup-agent", "owner down");
        var history = store.history("bank", "c1", "risk");
        assertEquals(2, history.size(), "prior owner's entry kept (not mutated)");
        assertEquals("risk-agent", history.get(0).writerAgentId(), "v1 still attributed to original owner");
        assertEquals("backup-agent", history.get(1).writerAgentId());
    }

    @Test
    void ownershipTransfersAfterSupersede() {
        store.append("bank", "c1", "risk", "C3", "risk-agent", null);
        store.supersede("bank", "c1", "risk", "C2", "backup-agent", "owner down");
        // backup-agent now owns it → can write normally; the original owner now cannot
        store.append("bank", "c1", "risk", "C1", "backup-agent", null);
        assertThrows(OwnershipViolationException.class,
                () -> store.append("bank", "c1", "risk", "back", "risk-agent", null));
    }

    @Test
    void supersessionIsRecordedInTheInteractionLog() {
        store.append("bank", "c1", "risk", "C3", "risk-agent", null);
        store.supersede("bank", "c1", "risk", "C2", "backup-agent", "risk-agent unavailable");
        assertTrue(store.interactions("bank", "c1").stream()
                        .anyMatch(e -> e.detail().contains("superseded") && e.actorAgentId().equals("backup-agent")),
                "the supersession is auditable in the team record");
    }
}
