package com.huawei.ascend.bus.forwarding.spi;

/**
 * Forwarding failure modes mirroring {@code ICD-Agent-Bus-Forwarding} Failure
 * Modes, plus {@code payload_ref_invalid} (Stage 7 runtime schema).
 *
 * <p>{@link #wireCode()} returns the snake_case ICD identifier — harness asserts
 * these verbatim so a renamed code surfaces as ICD / harness drift.
 *
 * <p>Retryable classification (per
 * {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §7}):
 * {@code ROUTE_NOT_FOUND}, {@code TENANT_MISMATCH}, {@code PAYLOAD_REF_INVALID}
 * are non-retryable; {@code DELIVERY_TIMEOUT}, {@code RECEIVER_UNAVAILABLE},
 * {@code BACKPRESSURE_REJECTED} are retryable; {@code DUPLICATE_SUPPRESSED} is a
 * dedup outcome, not a delivery failure.
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding} (Failure Modes);
 * {@code ICD-Agent-Bus-Forwarding-Runtime}.
 */
public enum ForwardingFailureCode {
    ROUTE_NOT_FOUND("route_not_found"),
    TENANT_MISMATCH("tenant_mismatch"),
    DELIVERY_TIMEOUT("delivery_timeout"),
    RECEIVER_UNAVAILABLE("receiver_unavailable"),
    BACKPRESSURE_REJECTED("backpressure_rejected"),
    DUPLICATE_SUPPRESSED("duplicate_suppressed"),
    PAYLOAD_REF_INVALID("payload_ref_invalid");

    private final String wireCode;

    ForwardingFailureCode(String wireCode) {
        this.wireCode = wireCode;
    }

    /** Snake_case ICD identifier (drift guard vs the runtime ICD / yaml). */
    public String wireCode() {
        return wireCode;
    }
}
