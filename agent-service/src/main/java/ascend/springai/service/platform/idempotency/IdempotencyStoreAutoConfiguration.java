package ascend.springai.service.platform.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(IdempotencyStore.class)
    IdempotencyStore jdbcIdempotencyStore(DataSource ds, IdempotencyProperties props) {
        LOG.info("Wiring JdbcIdempotencyStore (DataSource present, ttl={}).", props.ttl());
        return new JdbcIdempotencyStore(JdbcClient.create(ds), Clock.systemUTC(), props.ttl());
    }
}
