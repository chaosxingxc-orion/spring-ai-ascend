package com.huawei.ascend.examples.workmate.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "run_events")
public class RunEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "run_id", nullable = false, updatable = false, length = 64)
    private String runId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "event_name", nullable = false, length = 64)
    private String eventName;

    @Column(name = "payload_json", nullable = false, length = 65535)
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RunEvent() {
    }

    public RunEvent(
            UUID id,
            UUID sessionId,
            String runId,
            int seq,
            String eventName,
            String payloadJson) {
        this.id = id;
        this.sessionId = sessionId;
        this.runId = runId;
        this.seq = seq;
        this.eventName = eventName;
        this.payloadJson = payloadJson;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
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

    public String getEventName() {
        return eventName;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
