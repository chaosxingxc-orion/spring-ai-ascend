package com.huawei.ascend.service.runtime.runs;

import com.huawei.ascend.engine.orchestration.spi.RunMode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Contract-spine entity for the run lifecycle. Rule 11: tenant_id is mandatory.
 * W2: backed by a Postgres table via RunRepository (Spring Data JDBC CrudRepository). W0 dev: held
 * in-memory by InMemoryRunRegistry. See ADR-0021.
 *
 * <p>{@code mode} discriminates whether this run is executing a deterministic graph or
 * a ReAct agent loop. {@code parentNodeKey} and {@code suspendedAt} are populated only
 * when status = SUSPENDED.
 *
 * <p>L1.x Telemetry Vertical (ADR-0061 / ADR-0062 / §4 #54): {@code traceId} carries the
 * 32-char lowercase hex W3C trace id under which this Run started; {@code sessionId}
 * carries the optional N:M session correlation. Both are nullable at L1.x; W2 promotes
 * {@code traceId} to NOT NULL via Flyway {@code V2__run_trace_id_notnull.sql}.
 *
 * <p><b>Vocabulary Glossary.</b> Authority: ADR-0136 + ADR-0100.
 * {@code Run} is the <i>transient compute snapshot</i> layer of the 4-layer lifecycle
 * hierarchy <code>Run &le; Task &le; Session &le; Memory</code>. It is NOT
 * a synonym for {@link com.huawei.ascend.service.task.Task}, which is the
 * <i>control-state</i> layer (A2A protocol envelope state). Academic prose
 * that calls "Task" the scheduling-core maps to {@link com.huawei.ascend.service.task.Task};
 * the present-tense compute-pointer entity is {@code Run}. The two are deliberately
 * distinct (one Task may have many Runs cycling through PENDING/RUNNING/SUSPENDED;
 * see {@link com.huawei.ascend.service.task.Task} Javadoc).
 */
public record Run(
    UUID runId,
    String tenantId,
    String capabilityName,
    RunStatus status,
    RunMode mode,
    Instant createdAt,
    Instant updatedAt,
    Instant finishedAt,
    UUID parentRunId,
    Integer attemptId,
    String parentNodeKey,
    Instant suspendedAt,
    String traceId,
    String sessionId
) {
    public Run {
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(capabilityName, "capabilityName is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(mode, "mode is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    /**
     * Backward-compatible 12-arg constructor (pre-Telemetry-Vertical shape). Delegates
     * to the canonical constructor with {@code traceId} and {@code sessionId} set to
     * null. Existing call sites compile unchanged; new code calling the canonical
     * constructor is preferred.
     */
    public Run(UUID runId, String tenantId, String capabilityName, RunStatus status,
               RunMode mode, Instant createdAt, Instant updatedAt, Instant finishedAt,
               UUID parentRunId, Integer attemptId, String parentNodeKey, Instant suspendedAt) {
        this(runId, tenantId, capabilityName, status, mode, createdAt, updatedAt,
                finishedAt, parentRunId, attemptId, parentNodeKey, suspendedAt, null, null);
    }

    public Run withStatus(RunStatus newStatus) {
        RunStateMachine.validate(this.status, newStatus);
        return new Run(runId, tenantId, capabilityName, newStatus, mode,
                createdAt, Instant.now(), finishedAt, parentRunId, attemptId,
                parentNodeKey, suspendedAt, traceId, sessionId);
    }

    public Run withFinishedAt(Instant newFinishedAt) {
        return new Run(runId, tenantId, capabilityName, status, mode,
                createdAt, Instant.now(), newFinishedAt, parentRunId, attemptId,
                parentNodeKey, suspendedAt, traceId, sessionId);
    }

    public Run withUpdatedAt(Instant newUpdatedAt) {
        return new Run(runId, tenantId, capabilityName, status, mode,
                createdAt, newUpdatedAt, finishedAt, parentRunId, attemptId,
                parentNodeKey, suspendedAt, traceId, sessionId);
    }

    public Run withSuspension(String newParentNodeKey, Instant newSuspendedAt) {
        RunStateMachine.validate(this.status, RunStatus.SUSPENDED);
        return new Run(runId, tenantId, capabilityName, RunStatus.SUSPENDED, mode,
                createdAt, Instant.now(), finishedAt, parentRunId, attemptId,
                newParentNodeKey, newSuspendedAt, traceId, sessionId);
    }

    /**
     * Attach or replace the Telemetry Vertical trace identifier. Used by
     * {@code RunController} at Run-creation time and by the W2 resume path when the
     * checkpointer payload carries a parent_trace_id.
     */
    public Run withTraceId(String newTraceId) {
        return new Run(runId, tenantId, capabilityName, status, mode,
                createdAt, Instant.now(), finishedAt, parentRunId, attemptId,
                parentNodeKey, suspendedAt, newTraceId, sessionId);
    }

    /**
     * Attach or replace the session identifier (ADR-0062 N:M model).
     */
    public Run withSessionId(String newSessionId) {
        return new Run(runId, tenantId, capabilityName, status, mode,
                createdAt, Instant.now(), finishedAt, parentRunId, attemptId,
                parentNodeKey, suspendedAt, traceId, newSessionId);
    }
}
