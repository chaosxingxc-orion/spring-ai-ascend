package com.huawei.ascend.examples.workmate.session.dto;

import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import java.time.Instant;
import java.util.UUID;

/** Lightweight session row for sidebar / task list (no usage rollup or office layout). */
public record SessionSummaryResponse(
        UUID id,
        String title,
        String workspaceKey,
        SessionStatus status,
        String expertId,
        PermissionMode permissionMode,
        String modelId,
        String effort,
        Instant createdAt,
        Instant updatedAt,
        boolean pinned,
        Instant archivedAt) {

    public static SessionSummaryResponse from(WorkmateSession session, String workspaceKey) {
        return new SessionSummaryResponse(
                session.getId(),
                session.getTitle(),
                workspaceKey,
                session.getStatus(),
                session.getExpertId(),
                session.getPermissionMode(),
                session.getModelId(),
                session.getEffort(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.isPinned(),
                session.getArchivedAt());
    }
}
