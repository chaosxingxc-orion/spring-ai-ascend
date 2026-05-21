package com.huawei.ascend.service.platform.idempotency;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that IdempotencyHeaderFilter only applies to POST/PUT/PATCH.
 * GET, DELETE, HEAD, and OPTIONS must bypass the filter entirely (ADR-0027).
 */
class IdempotencyHeaderFilterMethodScopeTest {

    @ParameterizedTest
    @ValueSource(strings = {"GET", "DELETE", "HEAD", "OPTIONS"})
    void nonMutatingMethods_bypassFilter_evenWithoutHeader(String method) throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(null, registry, "research");
        MockHttpServletRequest req = new MockHttpServletRequest(method, "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(registry.counter(
                "springai_ascend_idempotency_header_missing_total", "posture", "research").count())
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void mutatingMethods_enforceHeader_inResearchPosture(String method) throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(null, registry, "research");
        MockHttpServletRequest req = new MockHttpServletRequest(method, "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
    }
}
