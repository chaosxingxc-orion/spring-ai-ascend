package ascend.springai.service.platform.tenant;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextFilterTest {

    @Test
    void missingHeader_devPosture_setsDefaultAndContinues() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TenantContextFilter filter = new TenantContextFilter(registry, "dev");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(registry.counter("springai_ascend_tenant_header_missing_total", "posture", "dev").count())
                .isEqualTo(1.0);
    }

    @Test
    void missingHeader_researchPosture_returns400() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TenantContextFilter filter = new TenantContextFilter(registry, "research");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(registry.counter("springai_ascend_tenant_header_missing_total", "posture", "research").count())
                .isEqualTo(1.0);
    }

    @Test
    void invalidUuid_returns400() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TenantContextFilter filter = new TenantContextFilter(registry, "dev");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/runs");
        req.addHeader(TenantConstants.HEADER_NAME, "not-a-uuid");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(registry.counter("springai_ascend_tenant_header_invalid_total", "posture", "dev").count())
                .isEqualTo(1.0);
    }

    @Test
    void validHeader_setsContextDuringChain() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TenantContextFilter filter = new TenantContextFilter(registry, "dev");
        String tenantId = "123e4567-e89b-12d3-a456-426614174000";
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/runs");
        req.addHeader(TenantConstants.HEADER_NAME, tenantId);
        MockHttpServletResponse res = new MockHttpServletResponse();
        TenantContext[] captured = new TenantContext[1];
        FilterChain chain = (req2, res2) -> { captured[0] = TenantContextHolder.get(); };

        filter.doFilter(req, res, chain);

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].tenantId().toString()).isEqualTo(tenantId);
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void missingHeader_prodPosture_returns400() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TenantContextFilter filter = new TenantContextFilter(registry, "prod");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(registry.counter("springai_ascend_tenant_header_missing_total", "posture", "prod").count())
                .isEqualTo(1.0);
    }

    @Test
    void missingHeader_counterAccumulatesAcrossRequests() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TenantContextFilter filter = new TenantContextFilter(registry, "research");
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/runs");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }
        assertThat(registry.counter("springai_ascend_tenant_header_missing_total", "posture", "research").count())
                .isEqualTo(3.0);
    }

    @Test
    void healthPath_notFiltered() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TenantContextFilter filter = new TenantContextFilter(registry, "research");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }
}
