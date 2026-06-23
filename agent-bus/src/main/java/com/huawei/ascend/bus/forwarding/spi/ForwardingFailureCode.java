package com.huawei.ascend.bus.forwarding.spi;

/**
 * Forwarding failure modes mirroring {@code ICD-Agent-Bus-Forwarding} Failure
 * Modes, plus {@code payload_ref_invalid} (Stage 7 runtime schema).
 *
 * <p>{@link #wireCode()} returns the snake_case ICD identifier — harness asserts
 * these verbatim so a renamed code surfaces as ICD / harness drift.
 *
 * <p>Stage 9 (MI9-004) adds an explicit {@link Classification} per code so the
 * worker / delivery result / record invariants can reject a wrong-class code at
 * construction time:
 * <ul>
 *   <li>{@code ROUTE_NOT_FOUND}, {@code TENANT_MISMATCH}, {@code PAYLOAD_REF_INVALID}
 *       — {@link Classification#NON_RETRYABLE non-retryable} (direct DLQ / reject).</li>
 *   <li>{@code REMOTE_TASK_FAILED} (Stage 18) — {@link Classification#NON_RETRYABLE
 *       non-retryable}: a remote agent reported a terminal business failure (A2A
 *       {@code FAILED} / {@code CANCELED} / {@code REJECTED}). Distinct from infra-layer
 *       retryable failures (timeout / receiver-unavailable): retrying the same input to a
 *       deterministically failing remote task would only bomb the downstream, so it routes
 *       straight to DLQ (direct DLQ, no retry budget consumed).</li>
 *   <li>{@code DELIVERY_TIMEOUT}, {@code RECEIVER_UNAVAILABLE},
 *       {@code BACKPRESSURE_REJECTED} — {@link Classification#RETRYABLE retryable}.</li>
 *   <li>{@code DUPLICATE_SUPPRESSED} — {@link Classification#DEDUP dedup outcome},
 *       not a delivery failure (never drives RETRY / DLQ).</li>
 * </ul>
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding} (Failure Modes);
 * {@code ICD-Agent-Bus-Forwarding-Runtime};
 * {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-outbox-inbox.md §7}.
 */
public enum ForwardingFailureCode {
    ROUTE_NOT_FOUND("route_not_found", Classification.NON_RETRYABLE),
    TENANT_MISMATCH("tenant_mismatch", Classification.NON_RETRYABLE),
    DELIVERY_TIMEOUT("delivery_timeout", Classification.RETRYABLE),
    RECEIVER_UNAVAILABLE("receiver_unavailable", Classification.RETRYABLE),
    BACKPRESSURE_REJECTED("backpressure_rejected", Classification.RETRYABLE),
    DUPLICATE_SUPPRESSED("duplicate_suppressed", Classification.DEDUP),
    PAYLOAD_REF_INVALID("payload_ref_invalid", Classification.NON_RETRYABLE),
    // Stage 18 (MI18-001): a remote agent's terminal business failure (A2A FAILED /
    // CANCELED / REJECTED) — non-retryable so it routes straight to DLQ instead of being
    // retried as a transient infra failure. See class javadoc.
    REMOTE_TASK_FAILED("remote_task_failed", Classification.NON_RETRYABLE);

    private final String wireCode;
    private final Classification classification;

    ForwardingFailureCode(String wireCode, Classification classification) {
        this.wireCode = wireCode;
        this.classification = classification;
    }

    /** Snake_case ICD identifier (drift guard vs the runtime ICD / yaml). */
    public String wireCode() {
        return wireCode;
    }

    /** Whether this code is a retryable delivery failure (may drive RETRY_SCHEDULED). */
    public boolean retryable() {
        return classification == Classification.RETRYABLE;
    }

    /** Whether this code is a non-retryable failure (direct DLQ / reject). */
    public boolean nonRetryable() {
        return classification == Classification.NON_RETRYABLE;
    }

    /** Whether this code is a dedup outcome (never a delivery failure). */
    public boolean dedup() {
        return classification == Classification.DEDUP;
    }

    /** Failure-code classification — drives RETRY / DLQ / REJECT routing. */
    public enum Classification {
        RETRYABLE, NON_RETRYABLE, DEDUP
    }
}
