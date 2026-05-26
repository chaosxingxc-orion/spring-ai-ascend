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
 * multiple Sessions.
 *
 * <p>A2A protocol state vocabulary alignment per
 * {@code docs/contracts/a2a-envelope.v1.yaml} (contract-only adoption;
 * NO SDK runtime dep).
 *
 * <p><b>Vocabulary Glossary.</b> Authority: ADR-0136 + ADR-0100.
 * Academic prose that refers to "Task as the scheduling core" maps to THIS class
 * (not to a renamed {@link com.huawei.ascend.service.runtime.runs.Run}).
 * Task is the <i>control-state</i> layer (done-or-not, why-stopped, A2A envelope state),
 * while {@code Run} is the <i>transient compute snapshot</i> layer (compute pointer +
 * delta + RunStatus DFA). The two are different entities in the 4-layer hierarchy;
 * one Task may have many transient Runs cycling through PENDING / RUNNING / SUSPENDED /
 * SUCCEEDED. See {@link com.huawei.ascend.service.runtime.runs.Run} Javadoc.
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
