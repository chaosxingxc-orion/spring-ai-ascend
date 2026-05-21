package com.huawei.ascend.service.task;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Task control-state entity.
 *
 * <p>The Task is the control-state layer in the Run ≤ Task ≤ Session ≤
 * Memory lifecycle hierarchy. Decoupled from SessionID: one Session
 * may concurrently execute multiple Tasks; one Task may drift across
 * multiple Sessions (.
 *
 * <p>A2A protocol state vocabulary alignment per
 * {@code docs/contracts/a2a-envelope.v1.yaml} (contract-only adoption;
 * NO SDK runtime dep.
 *
 * @param taskId     unique task identifier.
 * @param tenantId   mandatory per Rule R-C.c.
 * @param sessionId  current session anchor (nullable; tasks may drift).
 * @param taskKind   discriminator (interactive | batch | periodic | drift).
 * @param a2aState   A2A protocol envelope state (submitted | working | input_required | completed | failed).
 * @param stepNumber sequential step counter.
 * @param whyStopped reason for last suspension (nullable).
 * @param createdAt  creation timestamp.
 * @param updatedAt  last-update timestamp.
 */
public record Task(
        String taskId,
        String tenantId,
        String sessionId,
        TaskKind taskKind,
        A2aState a2aState,
        int stepNumber,
        String whyStopped,
        Instant createdAt,
        Instant updatedAt) {

    public Task {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(taskKind, "taskKind");
        Objects.requireNonNull(a2aState, "a2aState");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public Optional<String> sessionAnchor() {
        return Optional.ofNullable(sessionId);
    }

    public enum TaskKind {
        INTERACTIVE,
        BATCH,
        PERIODIC,
        DRIFT
    }

    /** A2A protocol envelope state, per docs/contracts/a2a-envelope.v1.yaml. */
    public enum A2aState {
        SUBMITTED,
        WORKING,
        INPUT_REQUIRED,
        COMPLETED,
        FAILED
    }
}
