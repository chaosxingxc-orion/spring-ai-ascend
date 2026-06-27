package com.huawei.ascend.examples.workmate.audit.chain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_chain")
public class AuditChainEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq_global", nullable = false, updatable = false)
    private Long seqGlobal;

    @Column(name = "run_event_id", nullable = false, updatable = false, unique = true)
    private UUID runEventId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "run_id", nullable = false, updatable = false, length = 64)
    private String runId;

    @Column(name = "event_name", nullable = false, updatable = false, length = 64)
    private String eventName;

    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Column(name = "prev_hash", nullable = false, updatable = false, length = 64)
    private String prevHash;

    @Column(name = "entry_hash", nullable = false, updatable = false, length = 64)
    private String entryHash;

    @Column(name = "category", nullable = false, updatable = false, length = 32)
    private String category;

    @Column(name = "decision", nullable = false, updatable = false, length = 32)
    private String decision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditChainEntry() {
    }

    public AuditChainEntry(
            UUID runEventId,
            UUID sessionId,
            String runId,
            String eventName,
            String payloadHash,
            String prevHash,
            String entryHash,
            String category,
            String decision,
            Instant createdAt) {
        this.runEventId = runEventId;
        this.sessionId = sessionId;
        this.runId = runId;
        this.eventName = eventName;
        this.payloadHash = payloadHash;
        this.prevHash = prevHash;
        this.entryHash = entryHash;
        this.category = category;
        this.decision = decision;
        this.createdAt = createdAt;
    }

    public Long getSeqGlobal() {
        return seqGlobal;
    }

    public UUID getRunEventId() {
        return runEventId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getRunId() {
        return runId;
    }

    public String getEventName() {
        return eventName;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public String getCategory() {
        return category;
    }

    public String getDecision() {
        return decision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
