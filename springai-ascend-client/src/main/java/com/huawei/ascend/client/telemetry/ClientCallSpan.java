package com.huawei.ascend.client.telemetry;

/**
 * One in-flight A2A call as seen by telemetry. The client drives the
 * lifecycle: exactly one of {@link #succeed} or {@link #fail} ends the span;
 * {@link #traceresponse} may be recorded before either.
 *
 * <p>No OpenTelemetry type appears here — this surface stays loadable on a
 * classpath without OTel.
 */
public interface ClientCallSpan {

    /**
     * The W3C {@code traceparent} header value derived from this span's
     * context (trace-id, span-id, sampled flag), so the wire trace joins the
     * locally captured span; null when this telemetry originates no trace
     * context and the client should fall back to its own header mint.
     */
    String traceparent();

    /** Records the server's {@code traceresponse} header; null is ignored. */
    void traceresponse(String traceresponse);

    /**
     * Ends the span for a call that returned.
     *
     * @param terminal     whether a terminal event was actually observed
     * @param responseText the user-visible answer text; attached only when the
     *                     posture allows PII on telemetry
     */
    void succeed(boolean terminal, String responseText);

    /** Ends the span for a call that threw, recording {@code error.type}. */
    void fail(Throwable error);
}
