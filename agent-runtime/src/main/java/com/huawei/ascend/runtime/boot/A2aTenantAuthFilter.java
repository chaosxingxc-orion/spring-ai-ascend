package com.huawei.ascend.runtime.boot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tenant authentication at the A2A ingress (cross-check model, ADR-0040):
 * the request MUST carry {@code Authorization: Bearer <jwt>} whose HS256
 * signature verifies and whose {@code tenant_id} claim, when an
 * {@code X-Tenant-Id} header is also present, MUST match it. The validated
 * tenant is published as the {@link #AUTHENTICATED_TENANT_ATTRIBUTE} request
 * attribute, which the controller prefers over the raw header.
 *
 * <p>Only active when {@code agent-runtime.access.a2a.jwt.enabled=true};
 * disabled deployments keep the header-attribution-only behavior.
 */
public final class A2aTenantAuthFilter extends OncePerRequestFilter {

    /** Request attribute carrying the JWT-authenticated tenant id. */
    public static final String AUTHENTICATED_TENANT_ATTRIBUTE = "a2a.authenticatedTenantId";

    private static final Logger log = LoggerFactory.getLogger(A2aTenantAuthFilter.class);

    private final JwtTenantValidator validator;

    public A2aTenantAuthFilter(RuntimeAccessProperties access) {
        this.validator = new JwtTenantValidator(
                access.getJwt().getHmacSecret(), access.getJwt().getClockSkewSeconds());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/a2a");
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
            log.warn("[A2A] rejected token: {}", e.getMessage());
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            return;
        }
        String headerTenant = request.getHeader("X-Tenant-Id");
        if (headerTenant != null && !headerTenant.isBlank()
                && !headerTenant.trim().equals(token.tenantId())) {
            // The cross-check: a client must not claim one tenant in the header
            // and authenticate as another.
            log.warn("[A2A] tenant cross-check failed: header={} claim={}",
                    headerTenant.trim(), token.tenantId());
            reject(response, HttpServletResponse.SC_FORBIDDEN,
                    "X-Tenant-Id does not match the authenticated tenant");
            return;
        }
        request.setAttribute(AUTHENTICATED_TENANT_ATTRIBUTE, token.tenantId());
        filterChain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":{\"status\":" + status
                + ",\"message\":\"" + message.replace("\"", "'") + "\"}}");
    }
}
