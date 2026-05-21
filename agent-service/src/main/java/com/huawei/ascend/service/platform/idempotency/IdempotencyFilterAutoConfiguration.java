package com.huawei.ascend.service.platform.idempotency;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdempotencyFilterAutoConfiguration {

    @Bean
    IdempotencyHeaderFilter idempotencyHeaderFilter(ObjectProvider<IdempotencyStore> storeProvider,
                                                    MeterRegistry registry,
                                                    @Value("${app.posture:dev}") String posture) {
        // Store may be absent in dev posture without Postgres and without
        // allow-in-memory; the filter degrades to header-only validation in that case.
        return new IdempotencyHeaderFilter(storeProvider.getIfAvailable(), registry, posture);
    }

    @Bean
    FilterRegistrationBean<IdempotencyHeaderFilter> idempotencyHeaderFilterRegistration(IdempotencyHeaderFilter filter) {
        FilterRegistrationBean<IdempotencyHeaderFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(30);
        return reg;
    }
}
