package ascend.springai.service.platform.tenant;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TenantContextFilter.class);

    private final String posture;
    private final Counter missingCounter;
    private final Counter invalidCounter;

    public TenantContextFilter(MeterRegistry registry, String posture) {
        this.posture = posture;
        this.missingCounter = Counter.builder("springai_ascend_tenant_header_missing_total")
                .tag("posture", posture).register(registry);
        this.invalidCounter = Counter.builder("springai_ascend_tenant_header_invalid_total")
                .tag("posture", posture).register(registry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // /v3/api-docs is permitted anonymously at W0 (contract gate uses it).
        return path.startsWith("/actuator") || "/v1/health".equals(path)
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(TenantConstants.HEADER_NAME);
        if (header == null || header.isBlank()) {
            missingCounter.increment();
            if ("dev".equalsIgnoreCase(posture)) {
                LOG.warn("X-Tenant-Id header missing; using dev default tenant in posture=dev");
                TenantContextHolder.set(
                        new TenantContext(UUID.fromString(TenantConstants.DEV_DEFAULT_TENANT_ID)));
                MDC.put("tenant_id", TenantConstants.DEV_DEFAULT_TENANT_ID);
                try {
                    chain.doFilter(request, response);
                } finally {
                    TenantContextHolder.clear();
                    MDC.remove("tenant_id");
                }
            } else {
                response.sendError(400, "X-Tenant-Id header is required");
            }
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(header.strip());
        } catch (IllegalArgumentException e) {
            invalidCounter.increment();
            LOG.warn("X-Tenant-Id header failed UUID validation; request rejected; posture={}", posture);
            response.sendError(400, "X-Tenant-Id must be a valid UUID");
            return;
        }
        TenantContextHolder.set(new TenantContext(uuid));
        MDC.put("tenant_id", uuid.toString());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove("tenant_id");
        }
    }
}
