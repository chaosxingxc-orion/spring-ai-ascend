package com.huawei.ascend.service.platform.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces ARCHITECTURE.md §4 #55 (W3C traceparent propagation at HTTP edge).
 *
 * <p>Verifies that {@link TraceExtractFilter}:
 * <ol>
 *   <li>Originates a fresh {@code trace_id} + {@code span_id} when no {@code traceparent}
 *       header is supplied; emits {@code traceresponse} on the response.</li>
 *   <li>Adopts the inbound {@code trace_id} when a well-formed {@code traceparent} is
 *       supplied; mints a fresh server {@code span_id} as the child of the inbound
 *       parent_span_id; surfaces both in MDC during the chain.</li>
 *   <li>Rejects malformed {@code traceparent} headers by falling back to a fresh trace
 *       and incrementing {@code springai_ascend_traceparent_invalid_total}.</li>
 *   <li>Clears MDC after the chain completes (no leak to subsequent requests).</li>
 * </ol>
 *
 * <p>Enforcer E41. ADR-0061 §4.
 */
class TraceExtractFilterIT {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void missing_traceparent_originates_fresh_trace_and_emits_traceresponse() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TraceExtractFilter filter = new TraceExtractFilter(registry, "dev");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();

        String[] mdcTrace = new String[2];
        FilterChain chain = (r, s) -> {
            mdcTrace[0] = MDC.get("trace_id");
            mdcTrace[1] = MDC.get("span_id");
        };

        filter.doFilter(req, res, chain);

        assertThat(mdcTrace[0]).as("trace_id originated in MDC").hasSize(32).matches("[0-9a-f]{32}");
        assertThat(mdcTrace[1]).as("span_id originated in MDC").hasSize(16).matches("[0-9a-f]{16}");
        assertThat(MDC.get("trace_id")).as("MDC cleared after chain").isNull();
        assertThat(MDC.get("span_id")).as("MDC cleared after chain").isNull();

        String traceresponse = res.getHeader(TraceExtractFilter.TRACERESPONSE_HEADER);
        assertThat(traceresponse).as("traceresponse emitted").matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(traceresponse).contains(mdcTrace[0]);

        assertThat(registry.counter("springai_ascend_trace_originated_total",
                "posture", "dev", "source", "server").count())
                .as("server-originated counter incremented").isEqualTo(1.0);
    }

    @Test
    void well_formed_traceparent_adopts_trace_id_and_mints_fresh_span() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TraceExtractFilter filter = new TraceExtractFilter(registry, "research");
        String inboundTrace = "0af7651916cd43dd8448eb211c80319c";
        String inboundParent = "b7ad6b7169203331";
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.addHeader("traceparent", "00-" + inboundTrace + "-" + inboundParent + "-01");
        MockHttpServletResponse res = new MockHttpServletResponse();

        String[] mdcTrace = new String[3];
        FilterChain chain = (r, s) -> {
            mdcTrace[0] = MDC.get("trace_id");
            mdcTrace[1] = MDC.get("span_id");
            mdcTrace[2] = MDC.get("parent_span_id");
        };

        filter.doFilter(req, res, chain);

        assertThat(mdcTrace[0]).as("inbound trace_id adopted").isEqualTo(inboundTrace);
        assertThat(mdcTrace[1]).as("server span_id minted fresh").hasSize(16)
                .isNotEqualTo(inboundParent);
        assertThat(mdcTrace[2]).as("inbound span_id surfaced as parent_span_id").isEqualTo(inboundParent);
        assertThat(res.getHeader(TraceExtractFilter.TRACERESPONSE_HEADER))
                .startsWith("00-" + inboundTrace + "-");

        assertThat(registry.counter("springai_ascend_trace_originated_total",
                "posture", "research", "source", "client").count())
                .as("client-source counter incremented for adopted trace").isEqualTo(1.0);
    }

    @Test
    void malformed_traceparent_originates_fresh_and_increments_invalid_counter() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TraceExtractFilter filter = new TraceExtractFilter(registry, "prod");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.addHeader("traceparent", "not-a-valid-traceparent");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (r, s) -> { /* no-op */ });

        assertThat(registry.counter("springai_ascend_traceparent_invalid_total",
                "posture", "prod").count())
                .as("invalid counter incremented").isEqualTo(1.0);
        assertThat(res.getHeader(TraceExtractFilter.TRACERESPONSE_HEADER))
                .as("traceresponse still emitted with fresh trace").matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    void all_zero_trace_id_is_rejected_as_malformed() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TraceExtractFilter filter = new TraceExtractFilter(registry, "research");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.addHeader("traceparent",
                "00-00000000000000000000000000000000-0000000000000000-01");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (r, s) -> { /* no-op */ });

        assertThat(registry.counter("springai_ascend_traceparent_invalid_total",
                "posture", "research").count())
                .as("all-zero id treated as invalid per W3C spec").isEqualTo(1.0);
    }

    @Test
    void health_and_actuator_paths_skip_filtering() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TraceExtractFilter filter = new TraceExtractFilter(registry, "dev");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/health");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (r, s) -> { /* no-op */ });

        assertThat(res.getHeader(TraceExtractFilter.TRACERESPONSE_HEADER))
                .as("health endpoint must not get a traceresponse — filter skipped").isNull();
    }
}
