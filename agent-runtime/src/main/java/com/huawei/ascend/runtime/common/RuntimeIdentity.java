package com.huawei.ascend.runtime.common;

import java.util.Objects;

/**
 * Universal identity for a runtime call — replaces the scattered
 * Handle/Scope/Params types. Fields ordered general→specific.
 *
 * <p>{@code parentTaskId}/{@code parentContextId} are the optional cross-run linkage to the
 * caller that invoked this run (a remote A2A parent agent); both null for a root run. They let
 * a sub-agent's trajectory reference the caller's run so a multi-agent flow can be stitched back
 * together. They are populated from inbound A2A metadata, never invented by the runtime.
 *
 * @param tenantId        mandatory
 * @param userId          mandatory
 * @param sessionId       mandatory
 * @param taskId          optional — set after task creation
 * @param agentId         mandatory
 * @param parentTaskId    optional — the caller's taskId (cross-run linkage), null for a root run
 * @param parentContextId optional — the caller's contextId, null for a root run
 */
public record RuntimeIdentity(
        String tenantId,
        String userId,
        String sessionId,
        String taskId,
        String agentId,
        String parentTaskId,
        String parentContextId) {

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

    /** Root-run convenience constructor (no parent linkage) — keeps pre-parent call sites unchanged. */
    public RuntimeIdentity(String tenantId, String userId, String sessionId, String taskId, String agentId) {
        this(tenantId, userId, sessionId, taskId, agentId, null, null);
    }

    /** Copy with a different taskId, preserving any parent linkage. */
    public RuntimeIdentity withTaskId(String newTaskId) {
        return new RuntimeIdentity(tenantId, userId, sessionId, newTaskId, agentId, parentTaskId, parentContextId);
    }

    /** Copy with cross-run parent linkage attached. */
    public RuntimeIdentity withParent(String parentTaskId, String parentContextId) {
        return new RuntimeIdentity(tenantId, userId, sessionId, taskId, agentId, parentTaskId, parentContextId);
    }

    /** Convenience: when taskId is not yet assigned. */
    public static RuntimeIdentity of(String tenantId, String userId, String sessionId, String agentId) {
        return new RuntimeIdentity(tenantId, userId, sessionId, null, agentId);
    }
}
