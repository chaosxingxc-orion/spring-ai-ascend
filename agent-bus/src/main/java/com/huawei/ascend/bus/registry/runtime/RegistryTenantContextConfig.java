package com.huawei.ascend.bus.registry.runtime;

import com.huawei.ascend.bus.registry.runtime.tenant.ThreadLocalTenantContext;
import com.huawei.ascend.bus.spi.registry.TenantContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembles the {@link TenantContext} application-context bean — the
 * ThreadLocal-backed implementation from the {@code tenant} subpackage.
 *
 * <p>The {@code tenant} subpackage itself must stay pure Java (ADR-0160 + ESC-2
 * design pivot — see {@code tenant.package-info}), so {@link ThreadLocalTenantContext}
 * carries no Spring annotation and is not component-scanned. The bean is declared
 * here in the {@code registry.runtime} top-level config plane (sibling to
 * {@link RegistrySchedulingConfig} / {@link RegistryObservabilityConfig} /
 * {@link RegistryObservabilityFallbackConfig}), mirroring how
 * {@code JdbcAgentRegistryRepository}'s {@code @Bean} is declared in
 * {@code RegistryPersistenceConfig} rather than on the adapter class.
 *
 * <p>Consumed by {@code PgMvpDiscoveryServiceImpl} (constructor parameter 1)
 * for the discovery-time tenant cross-check (S4 — when the context is bound
 * by a background scheduling caller, the discovery service cross-checks it
 * against the explicit {@code tenantId} parameter; unbound scopes skip the
 * check and rely on the explicit-parameter + WHERE-clause + RLS isolation
 * layers).
 *
 * <p>Authority: ADR-0160 decision 6 + ESC-2 design pivot; PR #389 review
 * issue #6 (agent-bus is a runnable Spring Boot application).
 */
@Configuration
public class RegistryTenantContextConfig {

    @Bean
    TenantContext tenantContext() {
        return new ThreadLocalTenantContext();
    }
}
