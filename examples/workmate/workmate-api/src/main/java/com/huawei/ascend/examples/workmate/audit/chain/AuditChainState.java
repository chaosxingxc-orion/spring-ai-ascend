package com.huawei.ascend.examples.workmate.audit.chain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "audit_chain_state")
public class AuditChainState {

    @Id
    @Column(name = "id", nullable = false)
    private short id = 1;

    @Column(name = "last_seq_global", nullable = false)
    private long lastSeqGlobal;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    @Column(name = "last_run_event_created_at")
    private Instant lastRunEventCreatedAt;

    @Column(name = "current_segment_date")
    private LocalDate currentSegmentDate;

    protected AuditChainState() {
    }

    public short getId() {
        return id;
    }

    public long getLastSeqGlobal() {
        return lastSeqGlobal;
    }

    public void setLastSeqGlobal(long lastSeqGlobal) {
        this.lastSeqGlobal = lastSeqGlobal;
    }

    public String getLastHash() {
        return lastHash;
    }

    public void setLastHash(String lastHash) {
        this.lastHash = lastHash;
    }

    public Instant getLastRunEventCreatedAt() {
        return lastRunEventCreatedAt;
    }

    public void setLastRunEventCreatedAt(Instant lastRunEventCreatedAt) {
        this.lastRunEventCreatedAt = lastRunEventCreatedAt;
    }

    public LocalDate getCurrentSegmentDate() {
        return currentSegmentDate;
    }

    public void setCurrentSegmentDate(LocalDate currentSegmentDate) {
        this.currentSegmentDate = currentSegmentDate;
    }
}
