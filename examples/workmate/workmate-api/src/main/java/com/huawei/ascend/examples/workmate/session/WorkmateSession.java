package com.huawei.ascend.examples.workmate.session;

import com.huawei.ascend.examples.workmate.connector.ConnectorIds;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class WorkmateSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "workspace_root", nullable = false, length = 2048)
    private String workspaceRoot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SessionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expert_id", length = 128)
    private String expertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_mode", nullable = false, length = 32)
    private PermissionMode permissionMode = PermissionMode.CRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_mode_before_plan", length = 32)
    private PermissionMode permissionModeBeforePlan;

    @Column(name = "conversation_generation", nullable = false)
    private int conversationGeneration = 0;

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "model_id", length = 128)
    private String modelId;

    @Column(name = "effort", length = 32)
    private String effort;

    @Column(name = "enabled_connector_ids", length = 4096)
    private String enabledConnectorIdsJson;

    @Column(name = "enabled_skill_ids", length = 4096)
    private String enabledSkillIdsJson;

    @Column(name = "pending_handoff_generation")
    private Integer pendingHandoffGeneration;

    @Column(name = "pending_handoff_from_expert_id", length = 128)
    private String pendingHandoffFromExpertId;

    protected WorkmateSession() {
    }

    public WorkmateSession(UUID id, String title, String workspaceRoot, SessionStatus status) {
        this(id, title, workspaceRoot, status, null, PermissionMode.CRAFT);
    }

    public WorkmateSession(UUID id, String title, String workspaceRoot, SessionStatus status, String expertId) {
        this(id, title, workspaceRoot, status, expertId, PermissionMode.CRAFT);
    }

    public WorkmateSession(
            UUID id,
            String title,
            String workspaceRoot,
            SessionStatus status,
            String expertId,
            PermissionMode permissionMode) {
        this.id = id;
        this.title = title;
        this.workspaceRoot = workspaceRoot;
        this.status = status;
        this.expertId = expertId;
        this.permissionMode = permissionMode != null ? permissionMode : PermissionMode.CRAFT;
        if (this.permissionMode == PermissionMode.PLAN) {
            this.permissionModeBeforePlan = PermissionMode.CRAFT;
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getExpertId() {
        return expertId;
    }

    public void setExpertId(String expertId) {
        this.expertId = expertId;
    }

    public PermissionMode getPermissionMode() {
        return permissionMode;
    }

    public void setPermissionMode(PermissionMode permissionMode) {
        this.permissionMode = permissionMode != null ? permissionMode : PermissionMode.CRAFT;
    }

    public PermissionMode getPermissionModeBeforePlan() {
        return permissionModeBeforePlan;
    }

    public void setPermissionModeBeforePlan(PermissionMode permissionModeBeforePlan) {
        this.permissionModeBeforePlan = permissionModeBeforePlan;
    }

    public int getConversationGeneration() {
        return conversationGeneration;
    }

    public void setConversationGeneration(int conversationGeneration) {
        this.conversationGeneration = Math.max(0, conversationGeneration);
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getEffort() {
        return effort;
    }

    public void setEffort(String effort) {
        this.effort = effort;
    }

    public List<String> getEnabledConnectorIds() {
        return ConnectorIds.normalize(StringListJson.read(enabledConnectorIdsJson));
    }

    public void setEnabledConnectorIds(List<String> connectorIds) {
        this.enabledConnectorIdsJson = StringListJson.write(ConnectorIds.normalize(connectorIds));
    }

    public List<String> getEnabledSkillIds() {
        return SessionSkillIds.normalize(StringListJson.read(enabledSkillIdsJson));
    }

    public void setEnabledSkillIds(List<String> skillIds) {
        this.enabledSkillIdsJson = StringListJson.write(SessionSkillIds.normalize(skillIds));
    }

    public Integer getPendingHandoffGeneration() {
        return pendingHandoffGeneration;
    }

    public void setPendingHandoffGeneration(Integer pendingHandoffGeneration) {
        this.pendingHandoffGeneration = pendingHandoffGeneration;
    }

    public String getPendingHandoffFromExpertId() {
        return pendingHandoffFromExpertId;
    }

    public void setPendingHandoffFromExpertId(String pendingHandoffFromExpertId) {
        this.pendingHandoffFromExpertId = pendingHandoffFromExpertId;
    }
}
