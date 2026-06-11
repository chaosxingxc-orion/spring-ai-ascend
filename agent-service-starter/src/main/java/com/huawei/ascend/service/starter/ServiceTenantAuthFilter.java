package com.huawei.ascend.service.starter;

import com.huawei.ascend.runtime.boot.JwtTenantValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tenant authentication at the service ingress (cross-check model, ADR-0040;
 * same semantics as the runtime's A2aTenantAuthFilter): the request MUST carry
 * {@code Authorization: Bearer <jwt>} whose HS256 signature verifies and whose
 * {@code tenant_id} claim, when an {@code X-Tenant-Id} header or a
 * {@code tenantId} query parameter is also present, MUST match it. The
 * validated tenant is published as the {@link #AUTHENTICATED_TENANT_ATTRIBUTE}
 * request attribute.
 *
 * <p>Only active when {@code agent-service.access.jwt.enabled=true}; disabled
 * deployments keep the header/parameter-attribution-only behavior. Guards the
 * registration, discovery (including A2A forwarding), and route-grant routes.
 */
public final class ServiceTenantAuthFilter extends OncePerRequestFilter {

    /** Request attribute carrying the JWT-authenticated tenant id. */
    public static final String AUTHENTICATED_TENANT_ATTRIBUTE = "agent-service.authenticatedTenantId";

    static final List<String> GUARDED_PATH_PREFIXES = List.of(
            "/v1/runtime-registrations",
            "/v1/agents",
            "/v1/route-grants");

    private static final Logger log = LoggerFactory.getLogger(ServiceTenantAuthFilter.class);

    private final JwtTenantValidator validator;

    public ServiceTenantAuthFilter(AgentServiceProperties properties) {
        AgentServiceProperties.Jwt jwt = properties.getAccess().getJwt();
        this.validator = new JwtTenantValidator(jwt.getHmacSecret(), jwt.getClockSkewSeconds());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return GUARDED_PATH_PREFIXES.stream().noneMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "missing bearer token");
            return;
        }
        JwtTenantValidator.ValidatedToken token;
        try {
            token = validator.validate(authorization.substring(7).trim());
        } catch (JwtTenantValidator.InvalidTokenException e) {
            log.warn("[service] rejected token: {}", e.getMessage());
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            return;
        }
        String headerTenant = request.getHeader("X-Tenant-Id");
        if (mismatches(headerTenant, token.tenantId())) {
            // The cross-check: a client must not claim one tenant in the header
            // and authenticate as another.
            log.warn("[service] tenant cross-check failed: header={} claim={}",
                    headerTenant.trim(), token.tenantId());
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "X-Tenant-Id does not match the authenticated tenant");
            return;
        }
        // The service routes carry the tenant claim as a query parameter, so the
        // same cross-check applies to it. (JSON request bodies are untouched —
        // getParameter only parses the query string for non-form content types.)
        String parameterTenant = request.getParameter("tenantId");
        if (mismatches(parameterTenant, token.tenantId())) {
            log.warn("[service] tenant cross-check failed: parameter={} claim={}",
                    parameterTenant.trim(), token.tenantId());
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "tenantId does not match the authenticated tenant");
            return;
        }
        request.setAttribute(AUTHENTICATED_TENANT_ATTRIBUTE, token.tenantId());
        filterChain.doFilter(request, response);
    }

    private static boolean mismatches(String claimedTenant, String authenticatedTenant) {
        return claimedTenant != null && !claimedTenant.isBlank()
                && !claimedTenant.trim().equals(authenticatedTenant);
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":{\"status\":" + status
                + ",\"message\":\"" + message.replace("\"", "'") + "\"}}");
    }
}
