package ascend.springai.service.runtime.orchestration;

import ascend.springai.engine.orchestration.spi.TraceContext;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * L1.x default {@link TraceContext} implementation. Propagates ids without emitting
 * spans. Pure-Java; no Spring, no OpenTelemetry. W2 replaces this with an
 * OTel-backed implementation via Spring autoconfiguration.
 *
 * <p>This class is the runtime-side counterpart of the HTTP-edge
 * {@code TraceExtractFilter} (post-Phase-C in
 * {@code agent-service/src/main/.../platform/observability/}; pre-Phase-C this
 * lived in the {@code agent-platform} module). Both originate ids using the same
 * W3C-compatible hex shapes so cross-module logs correlate.
 *
 * <p>Architecture reference: ADR-0061 §2, ARCHITECTURE.md §4 #53–#54.
 */
public final class NoopTraceContext implements TraceContext {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String traceId;
    private final String spanId;
    private final String sessionId;

    public NoopTraceContext(String traceId, String spanId, String sessionId) {
        this.traceId = Objects.requireNonNull(traceId, "traceId is required");
        this.spanId = Objects.requireNonNull(spanId, "spanId is required");
        this.sessionId = sessionId;
    }

    /**
     * Factory: originate a fresh root context with a random trace_id and span_id.
     * sessionId is null (callers populate it from the persisted Run when available).
     */
    public static NoopTraceContext newRoot() {
        return new NoopTraceContext(newTraceId(), newSpanId(), null);
    }

    /** Factory variant carrying a session id. */
    public static NoopTraceContext newRoot(String sessionId) {
        return new NoopTraceContext(newTraceId(), newSpanId(), sessionId);
    }

    @Override
    public String traceId() {
        return traceId;
    }

    @Override
    public String spanId() {
        return spanId;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public TraceContext newChildSpan(String name) {
        Objects.requireNonNull(name, "span name is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("span name must not be blank");
        }
        return new NoopTraceContext(traceId, newSpanId(), sessionId);
    }

    /** Mint a fresh 32-char lowercase hex trace_id (16 random bytes). */
    public static String newTraceId() {
        return randomHex(16);
    }

    /** Mint a fresh 16-char lowercase hex span_id (8 random bytes). */
    public static String newSpanId() {
        return randomHex(8);
    }

    private static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RNG.nextBytes(bytes);
        char[] out = new char[byteCount * 2];
        for (int i = 0; i < byteCount; i++) {
            int b = bytes[i] & 0xFF;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0F];
        }
        return new String(out);
    }
}
