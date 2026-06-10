package com.huawei.ascend.runtime.boot;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * W3C trace context at the HTTP edge (no OpenTelemetry SDK): extracts a
 * version-00 {@code traceparent} from every inbound request, or originates a
 * fresh trace when the header is missing or malformed. An unparseable id is
 * never propagated — a corrupt trace id would poison correlation on every
 * downstream log line, so falling back to a fresh trace is strictly safer.
 *
 * <p>For the duration of the request the filter publishes {@code trace_id} and
 * a freshly generated server {@code span_id} into the Logback MDC, and emits
 * {@code traceresponse: 00-&lt;trace_id&gt;-&lt;server_span_id&gt;-01} on every
 * response (success and error alike) so client SDKs can correlate. The header
 * is written before the chain proceeds so it survives an early response
 * commit.
 */
public final class TraceParentFilter extends OncePerRequestFilter {

    /** MDC key carrying the 32-char lowercase-hex W3C trace id. */
    public static final String TRACE_ID_MDC_KEY = "trace_id";

    /** MDC key carrying the 16-char lowercase-hex server span id. */
    public static final String SPAN_ID_MDC_KEY = "span_id";

    static final String TRACEPARENT_HEADER = "traceparent";
    static final String TRACERESPONSE_HEADER = "traceresponse";

    /** Counts inbound {@code traceparent} headers that were present but unparseable. */
    static final String INVALID_TRACEPARENT_COUNTER = "springai_ascend_traceparent_invalid_total";

    private static final int TRACE_ID_HEX_CHARS = 32;
    private static final int SPAN_ID_HEX_CHARS = 16;
    /** {@code 00-} + 32 hex + {@code -} + 16 hex + {@code -} + 2 hex. */
    private static final int TRACEPARENT_LENGTH = 55;

    /** Null when no MeterRegistry is available; the filter then runs unmetered. */
    private final Counter invalidTraceparentCounter;

    public TraceParentFilter() {
        this(null);
    }

    public TraceParentFilter(MeterRegistry meterRegistry) {
        this.invalidTraceparentCounter = meterRegistry == null ? null
                : Counter.builder(INVALID_TRACEPARENT_COUNTER)
                        .description("Inbound traceparent headers that were present but unparseable")
                        .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(TRACEPARENT_HEADER);
        String traceId = extractTraceId(header);
        if (traceId == null) {
            // A missing header is normal trace origination; only a header that was
            // sent but failed parsing signals a misbehaving client worth counting.
            if (header != null && invalidTraceparentCounter != null) {
                invalidTraceparentCounter.increment();
            }
            traceId = randomLowerHex(TRACE_ID_HEX_CHARS);
        }
        String spanId = randomLowerHex(SPAN_ID_HEX_CHARS);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        MDC.put(SPAN_ID_MDC_KEY, spanId);
        try {
            response.setHeader(TRACERESPONSE_HEADER, "00-" + traceId + "-" + spanId + "-01");
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
            MDC.remove(SPAN_ID_MDC_KEY);
        }
    }

    /**
     * Returns the trace id of a well-formed version-00 {@code traceparent}, or
     * null when the header is absent or fails any structural check (length,
     * separators, lowercase hex, all-zero trace/parent ids).
     */
    private static String extractTraceId(String header) {
        if (header == null) {
            return null;
        }
        String value = header.trim();
        if (value.length() != TRACEPARENT_LENGTH
                || !value.startsWith("00-")
                || value.charAt(35) != '-'
                || value.charAt(52) != '-') {
            return null;
        }
        String traceId = value.substring(3, 35);
        String parentId = value.substring(36, 52);
        String flags = value.substring(53);
        if (!isLowerHex(traceId) || !isLowerHex(parentId) || !isLowerHex(flags)) {
            return null;
        }
        if (isAllZero(traceId) || isAllZero(parentId)) {
            return null;
        }
        return traceId;
    }

    private static String randomLowerHex(int hexChars) {
        byte[] bytes = new byte[hexChars / 2];
        do {
            ThreadLocalRandom.current().nextBytes(bytes);
        } while (isAllZero(bytes));
        return HexFormat.of().formatHex(bytes);
    }

    private static boolean isLowerHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZero(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
