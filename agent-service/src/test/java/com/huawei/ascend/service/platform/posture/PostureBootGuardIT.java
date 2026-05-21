package com.huawei.ascend.service.platform.posture;

import com.huawei.ascend.service.platform.auth.AuthProperties;
import com.huawei.ascend.service.platform.idempotency.IdempotencyProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Layer-2 integration test for {@link PostureBootGuard} (ADR-0058).
 *
 * <p>Drives the guard directly with hand-built {@link GenericApplicationContext}
 * instances, avoiding the cost of a full Spring Boot bring-up per test case.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E11, #E21, #E22.
 */
class PostureBootGuardIT {

    private final PostureBootGuard guard = new PostureBootGuard();

    @Test
    void dev_posture_with_no_config_is_silent() {
        GenericApplicationContext ctx = ctxWith("dev", false, false, false, false);
        ApplicationReadyEvent event = new ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(), new String[0], ctx, Duration.ZERO);

        guard.onApplicationEvent(event);
        // No exception thrown = pass.
    }

    @Test
    void research_posture_without_jwks_config_fails_startup() {
        GenericApplicationContext ctx = ctxWith("research", false, true, true, true);
        ApplicationReadyEvent event = new ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(), new String[0], ctx, Duration.ZERO);

        assertThatThrownBy(() -> guard.onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth_jwks_config_missing");
    }

    @Test
    void research_posture_with_dev_local_mode_fails_startup() {
        GenericApplicationContext ctx = ctxWithAuth("research", true, true, true, true,
                new AuthProperties(null, null, null, null, null, true));
        ApplicationReadyEvent event = new ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(), new String[0], ctx, Duration.ZERO);

        assertThatThrownBy(() -> guard.onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev_local_mode_outside_dev");
    }

    @Test
    void prod_posture_without_datasource_fails_startup() {
        GenericApplicationContext ctx = ctxWith("prod", true, false, false, true);
        ApplicationReadyEvent event = new ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(), new String[0], ctx, Duration.ZERO);

        assertThatThrownBy(() -> guard.onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("datasource_missing");
    }

    @Test
    void prod_posture_with_in_memory_idempotency_fails_startup() {
        GenericApplicationContext ctx = ctxWithInMemoryStore("prod");
        ApplicationReadyEvent event = new ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(), new String[0], ctx, Duration.ZERO);

        assertThatThrownBy(() -> guard.onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("in_memory_idempotency_store_present");
    }

    @Test
    void research_posture_fully_configured_passes() {
        GenericApplicationContext ctx = ctxWith("research", true, true, true, true);
        ApplicationReadyEvent event = new ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(), new String[0], ctx, Duration.ZERO);

        guard.onApplicationEvent(event);
        // No exception thrown = pass.
    }

    // ----------------------------------------------------------------------

    /**
     * Build a minimal context with the requested combinations of beans.
     */
    private static GenericApplicationContext ctxWith(String posture,
                                                     boolean jwksConfigured,
                                                     boolean dataSourcePresent,
                                                     boolean jdbcStorePresent,
                                                     boolean meterRegistryPresent) {
        AuthProperties auth = jwksConfigured
                ? new AuthProperties("https://issuer.test", "https://jwks.test", "spring-ai-ascend",
                        Duration.ofSeconds(60), Duration.ofMinutes(5), false)
                : new AuthProperties(null, null, null, Duration.ofSeconds(60), Duration.ofMinutes(5), false);
        return ctxWithAuth(posture, jwksConfigured, dataSourcePresent, jdbcStorePresent, meterRegistryPresent, auth);
    }

    private static GenericApplicationContext ctxWithAuth(String posture,
                                                          boolean jwksConfigured,
                                                          boolean dataSourcePresent,
                                                          boolean jdbcStorePresent,
                                                          boolean meterRegistryPresent,
                                                          AuthProperties auth) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        MockEnvironment env = new MockEnvironment().withProperty("app.posture", posture);
        ctx.setEnvironment(env);
        ctx.getBeanFactory().registerSingleton("authProperties", auth);
        ctx.getBeanFactory().registerSingleton("idempotencyProperties",
                new IdempotencyProperties(Duration.ofHours(24), false));
        if (dataSourcePresent) {
            ctx.getBeanFactory().registerSingleton("dataSource", new StubDataSource());
        }
        if (jdbcStorePresent) {
            ctx.getBeanFactory().registerSingleton("idempotencyStore",
                    new com.huawei.ascend.service.platform.idempotency.JdbcIdempotencyStore(
                            null, java.time.Clock.systemUTC(), Duration.ofHours(24)));
        }
        if (meterRegistryPresent) {
            ctx.getBeanFactory().registerSingleton("meterRegistry", new SimpleMeterRegistry());
        }
        ctx.refresh();
        return ctx;
    }

    private static GenericApplicationContext ctxWithInMemoryStore(String posture) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.setEnvironment(new MockEnvironment().withProperty("app.posture", posture));
        ctx.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("override", Map.of("app.posture", posture)));
        ctx.getBeanFactory().registerSingleton("authProperties",
                new AuthProperties("https://issuer.test", "https://jwks.test", "spring-ai-ascend",
                        Duration.ofSeconds(60), Duration.ofMinutes(5), false));
        ctx.getBeanFactory().registerSingleton("dataSource", new StubDataSource());
        ctx.getBeanFactory().registerSingleton("idempotencyStore",
                new com.huawei.ascend.service.platform.idempotency.InMemoryIdempotencyStore(
                        java.time.Clock.systemUTC(), Duration.ofHours(24)));
        ctx.getBeanFactory().registerSingleton("meterRegistry", new SimpleMeterRegistry());
        ctx.refresh();
        return ctx;
    }

    /** Minimal non-null DataSource for bean-presence checks. */
    private static final class StubDataSource implements javax.sql.DataSource {
        @Override public java.sql.Connection getConnection() { throw new UnsupportedOperationException(); }
        @Override public java.sql.Connection getConnection(String u, String p) { throw new UnsupportedOperationException(); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @SuppressWarnings("unused")
    private MeterRegistry hold; // silence linter on shadowed import
}
