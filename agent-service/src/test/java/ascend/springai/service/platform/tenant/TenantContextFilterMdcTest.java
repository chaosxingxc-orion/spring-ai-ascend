package ascend.springai.service.platform.tenant;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies MDC tenant_id population and cleanup in TenantContextFilter (ADR-0023).
 */
class TenantContextFilterMdcTest {

    @AfterEach
    void clearMdc() {
        MDC.remove("tenant_id");
    }

    @Test
    void validHeader_populatesMdcDuringChain_andClearsAfter() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TenantContextFilter filter = new TenantContextFilter(registry, "dev");
        String tenantId = "123e4567-e89b-12d3-a456-426614174000";
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/runs");
        req.addHeader(TenantConstants.HEADER_NAME, tenantId);
        String[] mdcDuringChain = new String[1];
        FilterChain chain = (r, s) -> mdcDuringChain[0] = MDC.get("tenant_id");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(mdcDuringChain[0]).isEqualTo(tenantId);
        assertThat(MDC.get("tenant_id")).isNull();
    }

    @Test
    void devDefault_populatesMdcWithDefaultTenantId_andClearsAfter() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TenantContextFilter filter = new TenantContextFilter(registry, "dev");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/runs");
        String[] mdcDuringChain = new String[1];
        FilterChain chain = (r, s) -> mdcDuringChain[0] = MDC.get("tenant_id");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(mdcDuringChain[0]).isEqualTo(TenantConstants.DEV_DEFAULT_TENANT_ID);
        assertThat(MDC.get("tenant_id")).isNull();
    }

    @Test
    void openApiPath_notFiltered() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TenantContextFilter filter = new TenantContextFilter(registry, "research");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v3/api-docs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }
}
