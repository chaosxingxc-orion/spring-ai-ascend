package com.huawei.ascend.examples.workmate.session.dto;

import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.usage.SessionUsageTotals;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String title,
        String workspaceRoot,
        String workspaceKey,
        SessionStatus status,
        String expertId,
        PermissionMode permissionMode,
        PermissionMode permissionModeBeforePlan,
        long promptTokens,
        long completionTokens,
        Instant createdAt,
        Instant updatedAt,
        boolean pinned,
        Instant archivedAt,
        String modelId,
        String effort,
        String officeArtifactRoot,
        List<String> enabledConnectorIds,
        List<String> enabledSkillIds) {

    public static SessionResponse from(WorkmateSession session, String workspaceKey) {
        return from(session, workspaceKey, SessionUsageTotals.empty(), null);
    }

    public static SessionResponse from(
            WorkmateSession session, String workspaceKey, SessionUsageTotals usage) {
        return from(session, workspaceKey, usage, null);
    }

    public static SessionResponse from(
            WorkmateSession session, String workspaceKey, SessionUsageTotals usage, String officeArtifactRoot) {
        return new SessionResponse(
                session.getId(),
                session.getTitle(),
                session.getWorkspaceRoot(),
                workspaceKey,
                session.getStatus(),
                session.getExpertId(),
                session.getPermissionMode(),
                session.getPermissionModeBeforePlan(),
                usage.promptTokens(),
                usage.completionTokens(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.isPinned(),
                session.getArchivedAt(),
                session.getModelId(),
                session.getEffort(),
                officeArtifactRoot,
                session.getEnabledConnectorIds(),
                session.getEnabledSkillIds());
    }
}
