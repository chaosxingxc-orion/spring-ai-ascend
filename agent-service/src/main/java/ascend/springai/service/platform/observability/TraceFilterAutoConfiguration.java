package ascend.springai.service.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the L1.x Telemetry Vertical HTTP-edge filters.
 *
 * <p>Filter order:
 * <ul>
 *   <li>{@link TraceExtractFilter} at order 10 — runs first so all subsequent
 *       filters (auth, tenant, idempotency) can log under a populated MDC
 *       {@code trace_id} / {@code span_id}.</li>
 * </ul>
 *
 * <p>{@code JwtTenantClaimCrossCheck} stays at order 15; {@code TenantContextFilter}
 * stays at order 20. ADR-0061 §4.
 */
@Configuration
public class TraceFilterAutoConfiguration {

    @Bean
    TraceExtractFilter traceExtractFilter(MeterRegistry registry,
            @Value("${app.posture:dev}") String posture) {
        return new TraceExtractFilter(registry, posture);
    }

    @Bean
    FilterRegistrationBean<TraceExtractFilter> traceExtractFilterRegistration(
            TraceExtractFilter filter) {
        FilterRegistrationBean<TraceExtractFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(10);
        return reg;
    }
}
