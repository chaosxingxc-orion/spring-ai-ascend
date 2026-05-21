package com.huawei.ascend.service.platform.idempotency;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyHeaderFilterTest {

    @Test
    void missingHeader_devPosture_continuesChain() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(null, registry, "dev");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(registry.counter("springai_ascend_idempotency_header_missing_total", "posture", "dev").count())
                .isEqualTo(1.0);
    }

    @Test
    void missingHeader_researchPosture_returns400() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(null, registry, "research");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(registry.counter("springai_ascend_idempotency_header_missing_total", "posture", "research").count())
                .isEqualTo(1.0);
    }

    @Test
    void invalidUuid_returns400() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(null, registry, "dev");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.addHeader(IdempotencyConstants.HEADER_NAME, "not-a-uuid");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(registry.counter("springai_ascend_idempotency_header_invalid_total", "posture", "dev").count())
                .isEqualTo(1.0);
    }

    @Test
    void validHeader_continuesChain() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(null, registry, "dev");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.addHeader(IdempotencyConstants.HEADER_NAME, "123e4567-e89b-12d3-a456-426614174000");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void missingHeader_prodPosture_returns400() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(null, registry, "prod");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(registry.counter("springai_ascend_idempotency_header_missing_total", "posture", "prod").count())
                .isEqualTo(1.0);
    }

    @Test
    void missingHeader_counterAccumulatesAcrossRequests() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(null, registry, "research");
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }
        assertThat(registry.counter("springai_ascend_idempotency_header_missing_total", "posture", "research").count())
                .isEqualTo(3.0);
    }

    @Test
    void healthPath_notFiltered() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IdempotencyHeaderFilter filter = new IdempotencyHeaderFilter(null, registry, "research");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }
}
