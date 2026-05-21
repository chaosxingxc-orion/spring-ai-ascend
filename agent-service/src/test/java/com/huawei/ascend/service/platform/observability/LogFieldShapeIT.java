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
 * Enforces {@code docs/telemetry/policy.md §7} (Standard log fields) at L1.x.
 *
 * <p>Asserts that when the {@link TraceExtractFilter} runs ahead of business code,
 * the Logback MDC carries the full Telemetry-Vertical correlation set
 * ({@code trace_id}, {@code span_id}, {@code parent_span_id}) for log lines emitted
 * during the request scope. {@code tenant_id} MDC is owned by
 * {@link com.huawei.ascend.service.platform.tenant.TenantContextFilter} (verified separately by
 * {@code TenantContextFilterMdcTest}); {@code run_id} is owned by {@code RunController}
 * (verified separately by the controller IT).
 *
 * <p>Enforcer E42. ADR-0061 §4, §7.
 */
class LogFieldShapeIT {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void filter_populates_full_trace_mdc_set_during_chain() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TraceExtractFilter filter = new TraceExtractFilter(registry, "dev");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();

        String[] observed = new String[3];
        FilterChain chain = (r, s) -> {
            observed[0] = MDC.get("trace_id");
            observed[1] = MDC.get("span_id");
            observed[2] = MDC.get("parent_span_id");
        };

        filter.doFilter(req, res, chain);

        assertThat(observed[0]).as("trace_id MUST be in MDC during chain").isNotNull().matches("[0-9a-f]{32}");
        assertThat(observed[1]).as("span_id MUST be in MDC during chain").isNotNull().matches("[0-9a-f]{16}");
        assertThat(observed[2]).as("parent_span_id MUST be in MDC during chain").isNotNull().matches("[0-9a-f]{16}");
    }

    @Test
    void filter_clears_all_mdc_keys_after_chain() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TraceExtractFilter filter = new TraceExtractFilter(registry, "dev");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (r, s) -> { /* no-op */ });

        assertThat(MDC.get("trace_id")).as("MDC trace_id MUST be cleared").isNull();
        assertThat(MDC.get("span_id")).as("MDC span_id MUST be cleared").isNull();
        assertThat(MDC.get("parent_span_id")).as("MDC parent_span_id MUST be cleared").isNull();
    }

    @Test
    void filter_clears_mdc_even_when_chain_throws() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TraceExtractFilter filter = new TraceExtractFilter(registry, "research");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (r, s) -> {
            throw new RuntimeException("downstream-fault");
        };

        try {
            filter.doFilter(req, res, chain);
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(MDC.get("trace_id")).as("MDC must be cleared even on chain throw").isNull();
        assertThat(MDC.get("span_id")).isNull();
        assertThat(MDC.get("parent_span_id")).isNull();
    }
}
