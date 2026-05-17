package ascend.springai.service.platform.tenant;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantFilterAutoConfiguration {

    @Bean
    TenantContextFilter tenantContextFilter(MeterRegistry registry,
            @Value("${app.posture:dev}") String posture) {
        return new TenantContextFilter(registry, posture);
    }

    @Bean
    FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(20);
        return reg;
    }

    @Bean
    JwtTenantClaimCrossCheck jwtTenantClaimCrossCheck(MeterRegistry registry) {
        return new JwtTenantClaimCrossCheck(registry);
    }

    @Bean
    FilterRegistrationBean<JwtTenantClaimCrossCheck> jwtTenantClaimCrossCheckRegistration(
            JwtTenantClaimCrossCheck filter) {
        FilterRegistrationBean<JwtTenantClaimCrossCheck> reg = new FilterRegistrationBean<>(filter);
        // Order 15: after Spring Security's BearerTokenAuthenticationFilter (which
        // populates SecurityContextHolder), before TenantContextFilter (order 20).
        reg.setOrder(15);
        return reg;
    }
}
