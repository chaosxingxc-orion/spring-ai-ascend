package com.huawei.ascend.a2a.memory.a2a;

import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.InteractionEntry;
import com.huawei.ascend.a2a.memory.shared.InteractionEntry.InteractionType;
import com.huawei.ascend.a2a.memory.shared.SharedEntry;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import java.util.List;
import java.util.Optional;

/**
 * An A2A agent's view of its collaboration's shared blackboard — bound to the
 * collaboration (A2A contextId), tenant, and the calling agent. {@link #put} is
 * attributed to (and owned by) this agent; {@link #get} reads what any participant
 * wrote AND records a READ dependency edge (this agent → the owner) into the team
 * interaction record, so the collaboration's "who used whose conclusion" graph is
 * captured (no single agent's own conclusions hold it).
 */
public final class A2aSharedMemoryHandle {

    private final SharedMemoryKit kit;
    private final String agentId;

    A2aSharedMemoryHandle(SharedMemoryStore store, String tenantId, String collaborationId, String agentId,
            MemoryObserver observer) {
        this.kit = SharedMemoryKit.forCollaboration(store, tenantId, collaborationId, observer);
        this.agentId = agentId;
    }

    /** Write a conclusion, attributed to (and owned by) this agent. */
    public SharedEntry put(String key, String value) {
        return kit.put(key, value, agentId);
    }

    /** Idempotent write: a retry with the same {@code idempotencyKey} won't duplicate. */
    public SharedEntry put(String key, String value, String idempotencyKey) {
        return kit.put(key, value, agentId, idempotencyKey);
    }

    /** Read the latest value any participant wrote; records a READ dependency edge if present. */
    public Optional<String> get(String key) {
        Optional<SharedEntry> entry = kit.entry(key);
        entry.ifPresent(e -> kit.recordInteraction(new InteractionEntry(
                InteractionType.READ, agentId, e.writerAgentId(), key,
                "read v" + e.version(), System.currentTimeMillis())));
        return entry.map(SharedEntry::value);
    }

    /**
     * Take over a key whose owner is unavailable: appends a new version owned by
     * this agent, preserving the prior owner's history. Use under a trusted policy.
     */
    public SharedEntry supersedeUnavailable(String key, String value, String reason) {
        return kit.supersede(key, value, agentId, reason);
    }

    /** Record a hand-over of this collaboration's work from this agent to another. */
    public void recordHandover(String toAgentId, String detail) {
        kit.recordInteraction(new InteractionEntry(
                InteractionType.HANDOVER, agentId, toAgentId, "", detail, System.currentTimeMillis()));
    }

    /** Record the collaboration outcome (typically by the coordinator). */
    public void recordOutcome(String detail) {
        kit.recordInteraction(new InteractionEntry(
                InteractionType.OUTCOME, agentId, "", "", detail, System.currentTimeMillis()));
    }

    /** The collaboration's interaction record (team memory), oldest first. */
    public List<InteractionEntry> interactions() {
        return kit.interactions();
    }

    /** Keys visible on this collaboration's blackboard. */
    public List<String> keys() {
        return kit.keys();
    }

    /** The agent this handle writes as. */
    public String agentId() {
        return agentId;
    }
}
