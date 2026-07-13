package com.huawei.ascend.bus.forwarding.spi;

import java.util.Objects;

/**
 * Sender-side durable-queue record for the C3 outbox substrate (Stage 8 / 9).
 *
 * <p>Mirrors the outbox record schema of {@code ICD-Agent-Bus-Forwarding-Runtime}
 * field-for-field: the gateway writes a record when it accepts an envelope
 * ({@link ForwardingOutboxPort#enqueue}), and the dispatcher worker reads /
 * claims / mutates it. The record is the single source of truth a real JDBC
 * adapter must persist — it carries exactly the ICD fields plus the
 * Stage 8 additive {@link ForwardingLease} (claim / lease ownership).
 *
 * <p>{@code sourceServiceId} and {@code targetServiceId} live on the record, not
 * on {@link ForwardingEnvelope} (MI8-002). {@code targetServiceId} comes from
 * Stage 3 discovery metadata / gateway audit context — NOT by unpacking the
 * opaque {@code routeHandle} (MI9-005).
 *
 * <p>Stage 9 (MI9-003)固化 the runtime ICD condition-field rules in the compact
 * constructor: tenant continuity, {@code attemptCount >= 0}, and the per-status
 * lease / failure-code / next-attempt invariants (see
 * {@link #validateStatusInvariants}). A future edit that weakens any of these
 * fails the harness.
 *
 * <p>Forbidden-payload invariant (HD4): this record NEVER carries a payload body,
 * a token stream, Task execution state, or a physical endpoint. There are no
 * such fields, by design; large payloads take the {@code payloadRef} data
 * reference path.
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding-Runtime} (outbox record fields);
 * {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md §4.1};
 * {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md §3}.
 */
// scope: forwarding substrate — durable outbox record; never a payload body
public record ForwardingOutboxRecord(
        String tenantId,
        ForwardingMessageId messageId,
        String sourceServiceId,
        String targetServiceId,
        ForwardingRouteHandle routeHandle,
        String payloadRef,
        ForwardingStatus.Outbox status,
        int attemptCount,
        long nextAttemptAtMillisEpoch,
        long createdAtMillisEpoch,
        long updatedAtMillisEpoch,
        ForwardingFailureCode lastFailureCode,
        ForwardingLease lease,
        // FEAT-013 cross-hop correlation key (mirrored from ForwardingEnvelope.correlationId at enqueue,
        // like sourceServiceId/targetServiceId per L2 §2.3.1). Nullable: JDBC-loaded rows pass null (no V3
        // DDL for W2 — a real correlation_id column is deferred until FEAT-013 wires JDBC); non-null when
        // produced from an envelope via the in-memory outbox. The gateway (S2) matches responses by this field.
        String correlationId,
        // FEAT-013/014 event-type discriminator (mirrored from ForwardingEnvelope.eventType at enqueue).
        // Nullable: JDBC-loaded rows pass null; non-null when produced from an envelope. The gateway (S2)
        // classifies responses by this field (L2 §4.2), avoiding descriptor-encoding coupling.
        AgentBusEventType eventType
) {
    public ForwardingOutboxRecord {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(messageId, "messageId is required");
        requireNonBlank(sourceServiceId, "sourceServiceId");
        requireNonBlank(targetServiceId, "targetServiceId");
        Objects.requireNonNull(routeHandle, "routeHandle is required");
        Objects.requireNonNull(status, "status is required");
        if (payloadRef != null && payloadRef.isBlank()) {
            throw new IllegalArgumentException("payloadRef must be null or non-blank");
        }
        if (!tenantId.equals(routeHandle.tenantScope())) {
            throw new IllegalArgumentException(
                    "tenant_mismatch: outbox tenantId '" + tenantId
                    + "' must equal routeHandle tenantScope '" + routeHandle.tenantScope() + "'");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must be >= 0");
        }
        validateStatusInvariants(status, nextAttemptAtMillisEpoch, lastFailureCode, lease);
        // correlationId: null (JDBC back-compat / control-only) or non-blank (FEAT-013 correlation).
        if (correlationId != null && correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must be null or non-blank");
        }
    }

    /**
     * Per-status condition-field invariants (MI9-003). Extracted so the
     * constructor stays readable; exercised directly by the harness.
     */
    static void validateStatusInvariants(ForwardingStatus.Outbox status,
                                         long nextAttemptAtMillisEpoch,
                                         ForwardingFailureCode lastFailureCode,
                                         ForwardingLease lease) {
        switch (status) {
            case PENDING -> { /* fresh entry: no extra invariants */ }
            case DISPATCHING -> requireCondition(lease != null,
                    "DISPATCHING outbox record must hold a non-null lease (lease-safe, MI9-002)");
            case RETRY_SCHEDULED -> {
                requireCondition(nextAttemptAtMillisEpoch > 0,
                        "RETRY_SCHEDULED outbox record requires nextAttemptAtMillisEpoch > 0");
                requireCondition(lastFailureCode != null && lastFailureCode.retryable(),
                        "RETRY_SCHEDULED outbox record requires a retryable lastFailureCode (MI9-004)");
                requireCondition(lease == null,
                        "RETRY_SCHEDULED outbox record must not hold a lease (MI9-002)");
            }
            case ACKED -> {
                requireCondition(lastFailureCode == null,
                        "ACKED outbox record must not carry a lastFailureCode");
                requireCondition(lease == null,
                        "ACKED outbox record must not hold a lease (terminal, MI9-002)");
            }
            case DLQ -> {
                requireCondition(lastFailureCode != null,
                        "DLQ outbox record requires a lastFailureCode");
                requireCondition(lease == null,
                        "DLQ outbox record must not hold a lease (terminal, MI9-002)");
            }
            case EXPIRED -> {
                requireCondition(lastFailureCode != null,
                        "EXPIRED outbox record requires a lastFailureCode");
                requireCondition(lease == null,
                        "EXPIRED outbox record must not hold a lease (terminal, MI9-002)");
            }
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireCondition(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Whether this record currently holds an unexpired lease at the given instant. */
    public boolean isActivelyLeasedAt(long nowMillisEpoch) {
        return lease != null && !lease.isExpiredAt(nowMillisEpoch);
    }

    /** Whether this record is in a terminal state (no further dispatch). */
    public boolean isTerminal() {
        return status == ForwardingStatus.Outbox.ACKED
                || status == ForwardingStatus.Outbox.DLQ
                || status == ForwardingStatus.Outbox.EXPIRED;
    }
}
