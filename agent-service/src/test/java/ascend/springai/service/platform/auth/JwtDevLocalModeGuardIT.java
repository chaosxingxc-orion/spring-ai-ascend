package ascend.springai.service.platform.auth;

import ascend.springai.service.platform.idempotency.IdempotencyProperties;
import ascend.springai.service.platform.idempotency.JdbcIdempotencyStore;
import ascend.springai.service.platform.posture.PostureBootGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Layer-2 integration test for enforcer E11:
 * {@code app.auth.dev-local-mode=true} MUST NOT be honoured outside
 * {@code app.posture=dev}. {@link PostureBootGuard} (ADR-0058) is the gate.
 */
class JwtDevLocalModeGuardIT {

    @Test
    void dev_local_mode_true_in_research_posture_aborts_startup() {
        GenericApplicationContext ctx = ctxWith("research", true);
        ApplicationReadyEvent event = new ApplicationReadyEvent(
                new SpringApplication(), new String[0], ctx, Duration.ZERO);

        assertThatThrownBy(() -> new PostureBootGuard().onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev_local_mode_outside_dev");
    }

    @Test
    void dev_local_mode_true_in_prod_posture_aborts_startup() {
        GenericApplicationContext ctx = ctxWith("prod", true);
        ApplicationReadyEvent event = new ApplicationReadyEvent(
                new SpringApplication(), new String[0], ctx, Duration.ZERO);

        assertThatThrownBy(() -> new PostureBootGuard().onApplicationEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev_local_mode_outside_dev");
    }

    @Test
    void dev_local_mode_true_in_dev_posture_is_permitted() {
        GenericApplicationContext ctx = ctxWith("dev", true);
        ApplicationReadyEvent event = new ApplicationReadyEvent(
                new SpringApplication(), new String[0], ctx, Duration.ZERO);

        // No exception thrown = dev is permissive even with dev-local-mode=true.
        new PostureBootGuard().onApplicationEvent(event);
    }

    private static GenericApplicationContext ctxWith(String posture, boolean devLocalMode) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.setEnvironment(new MockEnvironment().withProperty("app.posture", posture));
        // Auth: fully configured so jwks check passes; only the devLocalMode flag varies.
        AuthProperties auth = new AuthProperties(
                "https://issuer.test", "https://jwks.test", "spring-ai-ascend",
                Duration.ofSeconds(60), Duration.ofMinutes(5), devLocalMode);
        ctx.getBeanFactory().registerSingleton("authProperties", auth);
        ctx.getBeanFactory().registerSingleton("idempotencyProperties",
                new IdempotencyProperties(Duration.ofHours(24), false));
        ctx.getBeanFactory().registerSingleton("dataSource", new StubDataSource());
        ctx.getBeanFactory().registerSingleton("idempotencyStore",
                new JdbcIdempotencyStore(null, Clock.systemUTC(), Duration.ofHours(24)));
        ctx.getBeanFactory().registerSingleton("meterRegistry", new SimpleMeterRegistry());
        ctx.refresh();
        return ctx;
    }

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
}
