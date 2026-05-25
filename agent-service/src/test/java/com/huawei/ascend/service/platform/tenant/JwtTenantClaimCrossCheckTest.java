package com.huawei.ascend.service.platform.tenant;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link JwtTenantClaimCrossCheck} (ADR-0056 §3, plan §7.2).
 * Layer 1: exercises each branch with mock request/response and a synthetic
 * {@link JwtAuthenticationToken} populating {@link SecurityContextHolder}.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E10.
 */
class JwtTenantClaimCrossCheckTest {

    private static final String TENANT_A = "00000000-0000-0000-0000-00000000000a";
    private static final String TENANT_B = "00000000-0000-0000-0000-00000000000b";

    private SimpleMeterRegistry registry;
    private JwtTenantClaimCrossCheck filter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        filter = new JwtTenantClaimCrossCheck(registry);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void no_authentication_means_pass_through() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.addHeader("X-Tenant-Id", TENANT_A);
        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls()).isEqualTo(1);
        assertThat(chain.request()).isSameAs(req);
        assertThat(chain.response()).isSameAs(res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void permit_list_path_skips_filter() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls()).isEqualTo(1);
        assertThat(chain.request()).isSameAs(req);
        assertThat(chain.response()).isSameAs(res);
    }

    @Test
    void missing_header_passes_through_to_let_TenantContextFilter_decide() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(TENANT_A));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls()).isEqualTo(1);
        assertThat(chain.request()).isSameAs(req);
        assertThat(chain.response()).isSameAs(res);
        assertThat(registry.find("springai_ascend_tenant_mismatch_total").counter().count()).isZero();
    }

    @Test
    void claim_equals_header_passes_through() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(TENANT_A));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.addHeader("X-Tenant-Id", TENANT_A);
        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls()).isEqualTo(1);
        assertThat(chain.request()).isSameAs(req);
        assertThat(chain.response()).isSameAs(res);
        assertThat(registry.find("springai_ascend_tenant_mismatch_total").counter().count()).isZero();
    }

    @Test
    void claim_differs_from_header_returns_403_with_tenant_mismatch() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(TENANT_A));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.addHeader("X-Tenant-Id", TENANT_B);
        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls()).isZero();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("\"code\":\"tenant_mismatch\"");
        assertThat(registry.find("springai_ascend_tenant_mismatch_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void claim_missing_and_header_present_returns_403_jwt_missing_tenant_claim() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(null));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/runs");
        req.addHeader("X-Tenant-Id", TENANT_A);
        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.calls()).isZero();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("\"code\":\"jwt_missing_tenant_claim\"");
        assertThat(registry.find("springai_ascend_jwt_missing_tenant_claim_total").counter().count())
                .isEqualTo(1.0);
    }

    private static JwtAuthenticationToken jwtAuth(String tenantClaim) {
        Map<String, Object> claims = (tenantClaim == null)
                ? Map.of("sub", "user-1")
                : Map.of("sub", "user-1", JwtTenantClaimCrossCheck.CLAIM_NAME, tenantClaim);
        Jwt jwt = new Jwt(
                "header.payload.sig",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                claims);
        return new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES);
    }

    private static final class RecordingFilterChain implements FilterChain {
        private int calls;
        private ServletRequest request;
        private ServletResponse response;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            this.calls++;
            this.request = request;
            this.response = response;
        }

        int calls() {
            return calls;
        }

        ServletRequest request() {
            return request;
        }

        ServletResponse response() {
            return response;
        }
    }
}
