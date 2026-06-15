package com.huawei.ascend.collab.core;

import java.util.Objects;
import java.util.UUID;

/**
 * Task token — the capability/correlation credential a coordinator issues when it
 * hands a sub-task to an agent. The agent MUST echo it back on its result; the
 * coordinator verifies it (a worker that can't present a valid, unexpired token
 * for the right task is rejected). Modelled on the platform's
 * {@code S2cCallbackEnvelope} discipline (correlation id + idempotency key +
 * deadline). Over A2A it rides on {@code Message.metadata}.
 *
 * @param tokenId        unique credential id (the thing the worker must echo)
 * @param taskId         the sub-task this token authorizes
 * @param capability     the capability the task was dispatched under
 * @param assignedAgentId the worker the coordinator dispatched to
 * @param tenantId       tenant scope (carried through every hop)
 * @param idempotencyKey dedupe key — a retry of the same dispatch reuses it
 * @param deadlineEpochMs absolute deadline; past it the token is invalid
 * @param issuedAtEpochMs issue time
 */
public record TaskToken(
        UUID tokenId,
        String taskId,
        String capability,
        String assignedAgentId,
        String tenantId,
        UUID idempotencyKey,
        long deadlineEpochMs,
        long issuedAtEpochMs) {

    public TaskToken {
        Objects.requireNonNull(tokenId, "tokenId");
        require(taskId, "taskId");
        require(capability, "capability");
        require(assignedAgentId, "assignedAgentId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (deadlineEpochMs <= 0) {
            throw new IllegalArgumentException("deadlineEpochMs must be positive");
        }
    }

    /** Issue a fresh token for a dispatch. {@code idempotencyKey} ties retries together. */
    public static TaskToken issue(String taskId, String capability, String assignedAgentId,
            String tenantId, UUID idempotencyKey, long ttlMs, long nowEpochMs) {
        return new TaskToken(UUID.randomUUID(), taskId, capability, assignedAgentId,
                tenantId == null ? "default" : tenantId,
                idempotencyKey == null ? UUID.randomUUID() : idempotencyKey,
                nowEpochMs + Math.max(1, ttlMs), nowEpochMs);
    }

    public boolean isExpiredAt(long nowEpochMs) {
        return nowEpochMs > deadlineEpochMs;
    }

    private static void require(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
