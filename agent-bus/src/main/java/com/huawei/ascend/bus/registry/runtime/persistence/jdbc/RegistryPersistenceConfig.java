package com.huawei.ascend.bus.registry.runtime.persistence.jdbc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Assembles the registry persistence adapter bean — the JDBC implementation of
 * {@link AgentRegistryRepository}.
 *
 * <p>Lives in {@code registry.runtime.persistence.jdbc} (the only registry
 * subpackage allowed to import {@code javax.sql} / Spring JDBC per ADR-0160
 * decision 4 — enforced by {@code AgentBusRegistryJdbcPurityTest}). The
 * bean <em>declaration</em> lives here rather than on the adapter class itself
 * to mirror the layering convention used by the forwarding side, where
 * {@code JdbcForwardingOutbox}'s {@code @Bean} is declared in
 * {@code AgentBusInfrastructureConfiguration}, not on the adapter class.
 *
 * <p>The bean is unconditionally assembled (no {@code @Profile}): the
 * {@code eventbus} process form needs the registry repository for its
 * co-deployed registry controller + probe scheduler + discovery service; a
 * standalone {@code gateway} process form does not use it but the bean is
 * harmless when idle (no scheduled work is triggered without the
 * {@code @Scheduled} probe method firing, which only activates under the
 * {@code eventbus} profile via {@code RegistrySchedulingConfig}).
 *
 * <p>Authority: ADR-0160 decision 4; PR #389 review issue #6 (agent-bus is a
 * runnable Spring Boot application); layering parity with
 * {@code AgentBusInfrastructureConfiguration#forwardingOutbox}.
 */
@Configuration
public class RegistryPersistenceConfig {

    /**
     * JDBC-backed {@link AgentRegistryRepository}. Uses the single-arg
     * {@link JdbcAgentRegistryRepository#JdbcAgentRegistryRepository(DataSource)}
     * constructor — the adapter internally builds its own
     * {@link org.springframework.transaction.support.TransactionTemplate} from
     * a {@link org.springframework.jdbc.datasource.DataSourceTransactionManager}
     * derived from the injected {@link DataSource}, so no external
     * {@code PlatformTransactionManager} bean is required.
     */
    @Bean
    AgentRegistryRepository agentRegistryRepository(DataSource dataSource) {
        return new JdbcAgentRegistryRepository(dataSource);
    }
}
