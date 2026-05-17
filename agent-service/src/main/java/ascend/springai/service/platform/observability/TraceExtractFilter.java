package ascend.springai.service.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Extracts or originates a W3C Trace Context {@code traceparent} on every inbound
 * request, populates Logback MDC with {@code trace_id} and {@code span_id}, and
 * emits {@code traceresponse} on the outbound response so client SDKs (W3) can
 * correlate.
 *
 * <p>Telemetry Vertical L1.x implementation. No OpenTelemetry SDK dependency —
 * pure Java parsing of the W3C wire format. W2 swaps this for an OTel SDK-backed
 * filter wired through {@code opentelemetry-spring-boot-starter}.
 *
 * <p>Architecture reference: ARCHITECTURE.md §0.5.3 (Telemetry Vertical) + §4 #55,
 * ADR-0061 §4, ADR-0063 §1.
 *
 * <p>Filter order (per {@code TraceFilterAutoConfiguration}): runs at order 10,
 * before {@code JwtTenantClaimCrossCheck} (15) and {@code TenantContextFilter} (20)
 * so the trace_id is available in MDC for any auth/tenant log lines.
 */
public class TraceExtractFilter extends OncePerRequestFilter {

    static final String TRACEPARENT_HEADER = "traceparent";
    static final String TRACERESPONSE_HEADER = "traceresponse";

    /** W3C version-00 traceparent: {@code 00-<32hex>-<16hex>-<2hex>}. */
    static final Pattern TRACEPARENT_RE = Pattern.compile(
            "^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final String ZERO_TRACE_ID = "00000000000000000000000000000000";
    private static final String ZERO_SPAN_ID = "0000000000000000";

    private final Counter originatedCounter;
    private final Counter adoptedCounter;
    private final Counter invalidCounter;

    public TraceExtractFilter(MeterRegistry registry, String posture) {
        this.originatedCounter = Counter.builder("springai_ascend_trace_originated_total")
                .tag("posture", posture).tag("source", "server").register(registry);
        this.adoptedCounter = Counter.builder("springai_ascend_trace_originated_total")
                .tag("posture", posture).tag("source", "client").register(registry);
        this.invalidCounter = Counter.builder("springai_ascend_traceparent_invalid_total")
                .tag("posture", posture).register(registry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || "/v1/health".equals(path)
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(TRACEPARENT_HEADER);
        String traceId;
        String parentSpanId;
        if (header != null && !header.isBlank()) {
            var matcher = TRACEPARENT_RE.matcher(header.strip());
            if (matcher.matches() && !ZERO_TRACE_ID.equals(matcher.group(1))
                    && !ZERO_SPAN_ID.equals(matcher.group(2))) {
                traceId = matcher.group(1);
                parentSpanId = matcher.group(2);
                adoptedCounter.increment();
            } else {
                invalidCounter.increment();
                traceId = newTraceId();
                parentSpanId = newSpanId();
                originatedCounter.increment();
            }
        } else {
            traceId = newTraceId();
            parentSpanId = newSpanId();
            originatedCounter.increment();
        }
        // Mint a fresh server-side span_id as a child of the inbound parent (or the
        // originated root). MDC carries the SERVER span_id so log lines correlate to
        // this request's work, not the client's pre-call span.
        String serverSpanId = newSpanId();
        MDC.put("trace_id", traceId);
        MDC.put("span_id", serverSpanId);
        MDC.put("parent_span_id", parentSpanId);
        try {
            response.setHeader(TRACERESPONSE_HEADER,
                    "00-" + traceId + "-" + serverSpanId + "-01");
            chain.doFilter(request, response);
        } finally {
            MDC.remove("trace_id");
            MDC.remove("span_id");
            MDC.remove("parent_span_id");
        }
    }

    /** Mint a fresh 32-char lowercase hex trace_id (16 random bytes). */
    static String newTraceId() {
        return randomHex(16);
    }

    /** Mint a fresh 16-char lowercase hex span_id (8 random bytes). */
    static String newSpanId() {
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
