package com.huawei.ascend.examples.workmate.session;

/**
 * Applies session-level permission mode changes with PLAN snapshot rules.
 */
public final class SessionPermissionModeTransition {

    private SessionPermissionModeTransition() {}

    public static void apply(WorkmateSession session, PermissionMode next) {
        if (session == null || next == null) {
            throw new IllegalArgumentException("session and permissionMode are required");
        }
        if (session.getArchivedAt() != null) {
            throw new IllegalArgumentException("Archived sessions cannot change permission mode");
        }
        PermissionMode current = session.getPermissionMode();
        if (current == next) {
            return;
        }
        if (current == PermissionMode.PLAN && next == PermissionMode.CRAFT) {
            throw new IllegalArgumentException(
                    "Use POST /api/v1/sessions/{id}/plan/confirm to switch from PLAN to CRAFT");
        }
        if (next == PermissionMode.PLAN && current != PermissionMode.PLAN) {
            session.setPermissionModeBeforePlan(current);
        } else if (current == PermissionMode.PLAN && next != PermissionMode.PLAN) {
            session.setPermissionModeBeforePlan(null);
        }
        session.setPermissionMode(next);
    }
}
