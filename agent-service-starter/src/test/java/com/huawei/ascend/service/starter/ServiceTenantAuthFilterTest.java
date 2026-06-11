package com.huawei.ascend.service.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The service-ingress half of the ADR-0040 cross-check: same validator and
 * semantics as the runtime's A2aTenantAuthFilter, applied to the
 * registration/discovery/route-grant routes, plus the service-specific
 * {@code tenantId} query-parameter cross-check.
 */
class ServiceTenantAuthFilterTest {

    private static final String SECRET = "service-test-secret-which-is-long-enough";

    private final ServiceTenantAuthFilter filter = new ServiceTenantAuthFilter(properties());

    @Test
    void missingBearerTokenIsRejected401() throws Exception {
        MockHttpServletRequest request = serviceRequest("/v1/agents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("missing bearer token");
    }

    @Test
    void badSignatureIsRejected401() throws Exception {
        MockHttpServletRequest request = serviceRequest("/v1/runtime-registrations");
        request.addHeader("Authorization",
                "Bearer " + jwt("bank-7", Instant.now().plusSeconds(300).getEpochSecond(), "wrong-secret"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("signature");
    }

    @Test
    void expiredTokenIsRejected401() throws Exception {
        MockHttpServletRequest request = serviceRequest("/v1/route-grants/resolve");
        request.addHeader("Authorization",
                "Bearer " + jwt("bank-7", Instant.now().minusSeconds(600).getEpochSecond(), SECRET));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("expired");
    }

    @Test
    void headerClaimMismatchIsRejected403() throws Exception {
        MockHttpServletRequest request = serviceRequest("/v1/agents");
        request.addHeader("X-Tenant-Id", "bank-OTHER");
        request.addHeader("Authorization",
                "Bearer " + jwt("bank-7", Instant.now().plusSeconds(300).getEpochSecond(), SECRET));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("does not match");
    }

    /** The service routes attribute the tenant by query parameter; the same cross-check applies. */
    @Test
    void queryParameterClaimMismatchIsRejected403() throws Exception {
        MockHttpServletRequest request = serviceRequest("/v1/agents");
        request.setParameter("tenantId", "bank-OTHER");
        request.addHeader("Authorization",
                "Bearer " + jwt("bank-7", Instant.now().plusSeconds(300).getEpochSecond(), SECRET));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("does not match");
    }

    @Test
    void validTokenWithMatchingAttributionsProceeds() throws Exception {
        MockHttpServletRequest request = serviceRequest("/v1/agents");
        request.addHeader("X-Tenant-Id", "bank-7");
        request.setParameter("tenantId", "bank-7");
        request.addHeader("Authorization",
                "Bearer " + jwt("bank-7", Instant.now().plusSeconds(300).getEpochSecond(), SECRET));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(ServiceTenantAuthFilter.AUTHENTICATED_TENANT_ATTRIBUTE))
                .isEqualTo("bank-7");
    }

    @Test
    void nonHs256AlgorithmIsRejected401() throws Exception {
        MockHttpServletRequest request = serviceRequest("/v1/agents");
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"tenant_id\":\"bank-7\"}");
        request.addHeader("Authorization", "Bearer " + header + "." + payload + ".AAAA");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("unsupported jwt algorithm");
    }

    @Test
    void unguardedPathsAreNotFiltered() throws Exception {
        MockHttpServletRequest request = serviceRequest("/v1/gateway-health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest serviceRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private static AgentServiceProperties properties() {
        AgentServiceProperties properties = new AgentServiceProperties();
        properties.getAccess().getJwt().setEnabled(true);
        properties.getAccess().getJwt().setHmacSecret(SECRET);
        return properties;
    }

    static String jwt(String tenantId, long exp, String secret) throws Exception {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"tenant_id\":\"" + tenantId + "\",\"exp\":" + exp + "}");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
