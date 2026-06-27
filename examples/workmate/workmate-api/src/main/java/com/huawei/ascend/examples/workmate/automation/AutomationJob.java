package com.huawei.ascend.examples.workmate.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_jobs")
public class AutomationJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "expert_id", length = 128)
    private String expertId;

    @Column(name = "prompt_text", nullable = false, columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "cron_expression", nullable = false, length = 128)
    private String cronExpression = "0 9 * * *";

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_session_id")
    private UUID lastSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", length = 32)
    private AutomationRunStatus lastStatus;

    @Column(name = "last_error", length = 2048)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExpertId() {
        return expertId;
    }

    public void setExpertId(String expertId) {
        this.expertId = expertId;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public UUID getLastSessionId() {
        return lastSessionId;
    }

    public void setLastSessionId(UUID lastSessionId) {
        this.lastSessionId = lastSessionId;
    }

    public AutomationRunStatus getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(AutomationRunStatus lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
