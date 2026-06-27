package com.huawei.ascend.examples.workmate.audit.chain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "audit_segments")
public class AuditSegment {

    @Id
    @Column(name = "segment_date", nullable = false)
    private LocalDate segmentDate;

    @Column(name = "entry_count", nullable = false)
    private long entryCount;

    @Column(name = "first_hash", nullable = false, length = 64)
    private String firstHash;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    protected AuditSegment() {
    }

    public AuditSegment(
            LocalDate segmentDate,
            long entryCount,
            String firstHash,
            String lastHash,
            String fileSha256,
            Instant closedAt) {
        this.segmentDate = segmentDate;
        this.entryCount = entryCount;
        this.firstHash = firstHash;
        this.lastHash = lastHash;
        this.fileSha256 = fileSha256;
        this.closedAt = closedAt;
    }

    public LocalDate getSegmentDate() {
        return segmentDate;
    }

    public long getEntryCount() {
        return entryCount;
    }

    public String getFirstHash() {
        return firstHash;
    }

    public String getLastHash() {
        return lastHash;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
