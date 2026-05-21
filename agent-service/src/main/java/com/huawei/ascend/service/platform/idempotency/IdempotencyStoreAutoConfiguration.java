package com.huawei.ascend.service.platform.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Wires exactly one {@link IdempotencyStore} bean per Spring context (Rule 6 /
 * ADR-0057 §3). Order:
 *
 * <ol>
 *   <li>{@link InMemoryIdempotencyStore} when {@code app.idempotency.allow-in-memory=true}
 *       AND {@code app.posture=dev}. (The posture cross-check itself is enforced at
 *       startup by {@code PostureBootGuard}, Phase F — this bean condition is
 *       intentionally narrow so misconfigured non-dev profiles never wire it.)</li>
 *   <li>Else {@link JdbcIdempotencyStore} when a {@link DataSource} bean is present.</li>
 *   <li>Else no bean — {@code IdempotencyHeaderFilter} falls back to header-only
 *       validation. {@code PostureBootGuard} aborts startup in research/prod.</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
public class IdempotencyStoreAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyStoreAutoConfiguration.class);

    @Bean
    @ConditionalOnExpression(
            "'${app.idempotency.allow-in-memory:false}'.equals('true') "
                    + "and '${app.posture:dev}'.equals('dev')")
    IdempotencyStore inMemoryIdempotencyStore(IdempotencyProperties props) {
        LOG.warn("Wiring InMemoryIdempotencyStore (posture=dev, allow-in-memory=true).");
        return new InMemoryIdempotencyStore(Clock.systemUTC(), props.ttl());
    }

    /**
     * Wires {@link JdbcIdempotencyStore} when no other {@link IdempotencyStore}
     * bean is registered AND a {@link DataSource} bean is available at injection
     * time. Uses {@link ObjectProvider} to defer DataSource resolution past the
     * Spring Boot 4 conditional-evaluation order hazard: a naive
     * {@code @ConditionalOnBean(DataSource.class)} on a regular
     * {@code @Configuration} class evaluates BEFORE
     * {@code DataSourceAutoConfiguration} registers the DataSource (the very
     * defect that produced the rc1→rc8 CI red trunk). {@code ObjectProvider}
     * is resolved at bean-factory time and returns {@code null} when no
     * DataSource exists; the method then returns {@code null} so Spring does
     * not register an {@link IdempotencyStore} bean — the same outcome the
     * dropped {@code @ConditionalOnBean(DataSource.class)} was meant to deliver.
     * Returning {@code null} from a {@code @Bean} method is documented Spring
     * behaviour: the registration is skipped and downstream
     * {@code @ConditionalOnMissingBean} consumers see the absence.
     *
     * <p>Note: the proper W2 fix is to convert this class to
     * a true Spring Boot auto-configuration via
     * {@code META-INF/spring/.../AutoConfiguration.imports} plus
     * {@code @AutoConfigureAfter(DataSourceAutoConfiguration.class)}. That
     * refactor is out of rc9 scope.
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    IdempotencyStore jdbcIdempotencyStore(ObjectProvider<DataSource> dsProvider, IdempotencyProperties props) {
        DataSource ds = dsProvider.getIfAvailable();
        if (ds == null) {
            LOG.debug("IdempotencyStore not registered: no DataSource bean available. PostureBootGuard will reject startup in research/prod.");
            return null;
        }
        LOG.info("Wiring JdbcIdempotencyStore (DataSource present, ttl={}).", props.ttl());
        return new JdbcIdempotencyStore(JdbcClient.create(ds), Clock.systemUTC(), props.ttl());
    }
}
