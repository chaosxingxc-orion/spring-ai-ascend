package ascend.springai.service.platform.tenant;

import ascend.springai.service.platform.web.ErrorEnvelopeWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * L1 filter: cross-checks the {@code tenant_id} claim on the validated JWT
 * against the {@code X-Tenant-Id} header (ADR-0056 §3, plan §7.2).
 *
 * <p>Runs after Spring Security's {@code BearerTokenAuthenticationFilter} (which
 * populates the {@link SecurityContextHolder} with a {@link JwtAuthenticationToken})
 * and before {@link TenantContextFilter} (order 20). FilterRegistrationBean order
 * is 15.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>No JWT in {@link SecurityContextHolder} (e.g. permit-list path, or zero-config
 *       dev posture) → pass through. Other filters still enforce header presence.</li>
 *   <li>JWT present, header missing → pass through; {@link TenantContextFilter}
 *       returns 400 downstream.</li>
 *   <li>JWT present with {@code tenant_id} claim, header present, equal → pass through.</li>
 *   <li>JWT present, claim missing, header present → 403 {@code jwt_missing_tenant_claim}.
 *       The JWT does not authorise the requested tenant.</li>
 *   <li>JWT present, claim and header both present, different → 403 {@code tenant_mismatch}.</li>
 * </ul>
 *
 * <p>Permit-list paths ({@code /v1/health}, {@code /actuator/*}, {@code /v3/api-docs*})
 * are skipped via {@link #shouldNotFilter(HttpServletRequest)} for symmetry with
 * {@link TenantContextFilter}.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E10.
 */
public class JwtTenantClaimCrossCheck extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTenantClaimCrossCheck.class);

    static final String CLAIM_NAME = "tenant_id";

    private final Counter mismatchCounter;
    private final Counter missingClaimCounter;

    public JwtTenantClaimCrossCheck(MeterRegistry registry) {
        this.mismatchCounter = Counter.builder("springai_ascend_tenant_mismatch_total")
                .description("JWT tenant_id claim did not match the X-Tenant-Id header.")
                .register(registry);
        this.missingClaimCounter = Counter.builder("springai_ascend_jwt_missing_tenant_claim_total")
                .description("JWT was accepted but carried no tenant_id claim while a tenant header was present.")
                .register(registry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || "/v1/health".equals(path)
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String headerTenant = request.getHeader(TenantConstants.HEADER_NAME);
        if (headerTenant == null || headerTenant.isBlank()) {
            // Header validation belongs to TenantContextFilter (order 20).
            chain.doFilter(request, response);
            return;
        }

        String claimTenant = jwt.getClaimAsString(CLAIM_NAME);
        if (claimTenant == null || claimTenant.isBlank()) {
            missingClaimCounter.increment();
            LOG.warn("JWT has no tenant_id claim but X-Tenant-Id={} was provided; rejecting", headerTenant);
            ErrorEnvelopeWriter.write(response, HttpServletResponse.SC_FORBIDDEN,
                    "jwt_missing_tenant_claim",
                    "Authenticated principal does not authorise any tenant.");
            return;
        }

        if (!claimTenant.equals(headerTenant.strip())) {
            mismatchCounter.increment();
            LOG.warn("Tenant mismatch: jwt.tenant_id={} X-Tenant-Id={}; rejecting",
                    claimTenant, headerTenant);
            ErrorEnvelopeWriter.write(response, HttpServletResponse.SC_FORBIDDEN,
                    "tenant_mismatch",
                    "Request tenant does not match authenticated tenant.");
            return;
        }

        chain.doFilter(request, response);
    }
}
