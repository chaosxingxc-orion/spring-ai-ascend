package com.huawei.ascend.bus.registry.runtime;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process {@link MeterRegistry} fallback used when no actuator is on the
 * classpath (ADR-0160 decision 7 — agent-bus boots standalone without
 * spring-boot-starter-actuator). When actuator IS present, its
 * auto-configured {@code MeterRegistry} bean wins and this fallback is
 * suppressed via {@link ConditionalOnMissingBean}. Consumed by
 * {@link RegistryObservabilityConfig} for the registry audit/metrics facade.
 *
 * <p>Lives in the {@code registry.runtime} top-level config plane (sibling to
 * {@link RegistrySchedulingConfig} / {@link RegistryObservabilityConfig})
 * because it has no JDBC dependency — it only touches micrometer-core, which
 * is unrestricted across the registry runtime tree.
 *
 * <p>Authority: ADR-0160 decision 7; PR #389 review issue #6 (agent-bus is a
 * runnable Spring Boot application).
 */
@Configuration
public class RegistryObservabilityFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
