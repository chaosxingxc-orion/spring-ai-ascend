package ascend.springai.service.platform.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * bean is registered. The {@code DataSource} parameter is auto-injected by
     * Spring; absence of a {@code DataSource} bean fails autowiring with a
     * clear {@code NoSuchBeanDefinitionException}, which is the correct
     * production behavior under {@code research}/{@code prod} posture (a
     * durable backing store is mandatory — see {@code PostureBootGuard}).
     *
     * <p>Note (rc9 / 2026-05-19): an earlier revision carried
     * {@code @ConditionalOnBean(DataSource.class)} to allow graceful absence
     * of a DataSource bean. Under Spring Boot 4 that condition evaluates on
     * regular {@code @Configuration} classes BEFORE
     * {@code DataSourceAutoConfiguration} registers the DataSource, so the
     * condition mis-fires and {@code jdbcIdempotencyStore} silently fails to
     * register — observed in CI as {@code No qualifying bean of type
     * IdempotencyStore} across every {@code @Testcontainers} IT. The
     * condition is dropped; the alternative (converting this class to a true
     * auto-configuration with {@code @AutoConfigureAfter(DataSourceAutoConfiguration.class)}
     * + {@code META-INF/spring/.../AutoConfiguration.imports}) is W2 work.
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    IdempotencyStore jdbcIdempotencyStore(DataSource ds, IdempotencyProperties props) {
        LOG.info("Wiring JdbcIdempotencyStore (DataSource present, ttl={}).", props.ttl());
        return new JdbcIdempotencyStore(JdbcClient.create(ds), Clock.systemUTC(), props.ttl());
    }
}
