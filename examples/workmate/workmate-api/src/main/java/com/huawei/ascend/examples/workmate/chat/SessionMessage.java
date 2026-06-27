package com.huawei.ascend.examples.workmate.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_messages")
public class SessionMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "payload_json", nullable = false, length = 65535)
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "superseded", nullable = false)
    private boolean superseded = false;

    protected SessionMessage() {
    }

    public SessionMessage(
            String id,
            UUID sessionId,
            String runId,
            int seq,
            String payloadJson) {
        this.id = id;
        this.sessionId = sessionId;
        this.runId = runId;
        this.seq = seq;
        this.payloadJson = payloadJson;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getRunId() {
        return runId;
    }

    public int getSeq() {
        return seq;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isSuperseded() {
        return superseded;
    }

    public void setSuperseded(boolean superseded) {
        this.superseded = superseded;
    }
}
