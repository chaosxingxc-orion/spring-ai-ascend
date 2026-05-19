package ascend.springai.engine.orchestration.spi;

/**
 * Per-Run trace correlation carrier. SPI companion of {@link RunContext} for the
 * Telemetry Vertical (ADR-0061). Pure Java — no Spring, no OpenTelemetry, no
 * Micrometer types, per ARCHITECTURE.md §4 #7.
 *
 * <p>L1.x contract surface: ids are plain Strings (W3C-compatible hex). At L1.x
 * the default implementation is {@link ascend.springai.service.runtime.orchestration.NoopTraceContext},
 * which propagates ids without emitting spans. W2 wires an OTel-backed implementation
 * that bridges {@link #newChildSpan(String)} into an OpenTelemetry {@code Tracer}.
 *
 * <p>Architecture reference: ARCHITECTURE.md §0.5.3 (Telemetry Vertical) +
 * §4 #53–#55, ADR-0061, ADR-0062.
 */
public interface TraceContext {

    /**
     * W3C-compatible 32-character lowercase hex trace identifier (16 random bytes
     * rendered). Stable for the lifetime of the trace. MUST be non-null when this
     * context is bound to an in-flight Run.
     */
    String traceId();

    /**
     * W3C-compatible 16-character lowercase hex span identifier (8 random bytes
     * rendered) for the current span in this context. Each {@link #newChildSpan(String)}
     * returns a fresh context with a new span_id and parentSpanId set to the caller's
     * span_id.
     */
    String spanId();

    /**
     * Optional session identifier — the Trace ↔ Run ↔ Session N:M model (ADR-0062).
     * MAY be null at L1.x; W2 makes this non-null in posture=research/prod.
     */
    String sessionId();

    /**
     * Open a child span under this context. The returned context inherits trace_id
     * and session_id; its parent_span_id equals this context's span_id; its span_id
     * is freshly generated.
     *
     * <p>At L1.x the {@link ascend.springai.service.runtime.orchestration.NoopTraceContext}
     * implementation does not emit a span — it only mints a new id pair. W2 wires
     * the real OTel {@code Tracer.spanBuilder(...)} path.
     *
     * @param name span name; non-null, non-blank
     * @return a fresh child {@code TraceContext}; never null
     */
    TraceContext newChildSpan(String name);
}
