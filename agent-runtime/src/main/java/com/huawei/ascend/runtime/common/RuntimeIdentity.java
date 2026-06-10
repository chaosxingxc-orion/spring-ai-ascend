package com.huawei.ascend.runtime.common;

import java.util.Objects;

/**
 * Universal identity for a runtime call — replaces the scattered
 * Handle/Scope/Params types. Fields ordered general→specific.
 * Uses the same field order as the former RuntimeIdentity so
 * existing call sites don't break.
 *
 * @param tenantId  mandatory
 * @param userId    mandatory
 * @param sessionId mandatory
 * @param taskId    optional — set after task creation
 * @param agentId   mandatory
 */
public record RuntimeIdentity(
        String tenantId,
        String userId,
        String sessionId,
        String taskId,
        String agentId) {

    public RuntimeIdentity {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(agentId, "agentId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
    }

    /** Copy with a different taskId. */
    public RuntimeIdentity withTaskId(String newTaskId) {
        return new RuntimeIdentity(tenantId, userId, sessionId, newTaskId, agentId);
    }

    /** Convenience: when taskId is not yet assigned. */
    public static RuntimeIdentity of(String tenantId, String userId, String sessionId, String agentId) {
        return new RuntimeIdentity(tenantId, userId, sessionId, null, agentId);
    }
}
