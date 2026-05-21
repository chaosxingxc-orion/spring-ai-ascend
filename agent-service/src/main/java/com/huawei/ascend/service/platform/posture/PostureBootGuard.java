package com.huawei.ascend.service.platform.posture;

import com.huawei.ascend.service.platform.auth.AuthProperties;
import com.huawei.ascend.service.platform.idempotency.IdempotencyStore;
import com.huawei.ascend.service.platform.idempotency.InMemoryIdempotencyStore;
import com.huawei.ascend.service.platform.idempotency.JdbcIdempotencyStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Boot-time fail-closed gate (ADR-0058).
 *
 * <p>On {@link ApplicationReadyEvent} inspects {@code app.posture}; for
 * {@code research}/{@code prod} runs the required-config matrix and throws
 * {@link IllegalStateException} (aborting startup) on any failure. {@code dev}
 * is permissive: warnings only, never fatal.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E11, #E21, #E22.
 */
@Configuration(proxyBeanMethods = false)
public class PostureBootGuard implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(PostureBootGuard.class);

    private static final String COUNTER_NAME = "springai_ascend_posture_boot_failure_total";

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        Environment env = ctx.getEnvironment();
        String posture = env.getProperty("app.posture", "dev").toLowerCase();

        if ("dev".equals(posture)) {
            LOG.debug("PostureBootGuard: posture=dev; no required-config checks.");
            return;
        }

        List<String> failures = runChecks(ctx, posture);

        if (failures.isEmpty()) {
            LOG.info("PostureBootGuard: posture={} required-config matrix satisfied.", posture);
            return;
        }

        MeterRegistry meters = beanIfPresent(ctx, MeterRegistry.class);
        if (meters != null) {
            for (String reason : failures) {
                Counter.builder(COUNTER_NAME)
                        .description("Posture boot guard violations at startup.")
                        .tag("posture", posture)
                        .tag("reason", reasonTag(reason))
                        .register(meters)
                        .increment();
            }
        }

        String message = "PostureBootGuard rejected startup (posture=" + posture + "):\n  - "
                + String.join("\n  - ", failures);
        LOG.error(message);
        throw new IllegalStateException(message);
    }

    private List<String> runChecks(ApplicationContext ctx, String posture) {
        List<String> failures = new ArrayList<>();

        // 1) AuthProperties: issuer/jwks-uri/audience all set.
        AuthProperties auth = beanIfPresent(ctx, AuthProperties.class);
        check(failures, "auth_jwks_config_missing",
                () -> auth != null && auth.hasJwksConfig(),
                "app.auth.{issuer,jwks-uri,audience} must all be set");

        // 2) dev-local-mode MUST NOT be enabled outside dev posture.
        check(failures, "dev_local_mode_outside_dev",
                () -> auth == null || !auth.devLocalMode(),
                "app.auth.dev-local-mode=true is only valid when app.posture=dev");

        // 3) DataSource bean present.
        check(failures, "datasource_missing",
                () -> beanIfPresent(ctx, DataSource.class) != null,
                "no DataSource bean — durable persistence required in " + posture);

        // 4) IdempotencyStore is JdbcIdempotencyStore (not in-memory).
        IdempotencyStore store = beanIfPresent(ctx, IdempotencyStore.class);
        check(failures, "idempotency_store_not_durable",
                () -> store instanceof JdbcIdempotencyStore,
                "IdempotencyStore bean must be JdbcIdempotencyStore; in-memory not allowed in " + posture);
        check(failures, "in_memory_idempotency_store_present",
                () -> !(store instanceof InMemoryIdempotencyStore),
                "InMemoryIdempotencyStore must not be registered in " + posture);

        // 5) MeterRegistry present (observability mandate).
        check(failures, "meter_registry_missing",
                () -> beanIfPresent(ctx, MeterRegistry.class) != null,
                "no MeterRegistry bean — observability is mandatory in " + posture);

        return failures;
    }

    private static void check(List<String> failures, String tag, Supplier<Boolean> condition, String detail) {
        try {
            if (!condition.get()) {
                failures.add(tag + ": " + detail);
            }
        } catch (RuntimeException e) {
            failures.add(tag + ": check raised " + e.getClass().getSimpleName()
                    + "(" + Optional.ofNullable(e.getMessage()).orElse("") + ")");
        }
    }

    private static <T> T beanIfPresent(ApplicationContext ctx, Class<T> type) {
        try {
            return ctx.getBeanProvider(type).getIfAvailable();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Strips the descriptive suffix so the tag stays low-cardinality. */
    private static String reasonTag(String fullMessage) {
        int colon = fullMessage.indexOf(':');
        return (colon > 0) ? fullMessage.substring(0, colon) : fullMessage;
    }

    // Compile-time witness that the dependent beans actually exist in the
    // platform module. Unused parameters; the field forces classpath presence.
    @SuppressWarnings("unused")
    private static final Class<?> WITNESS = ObjectProvider.class;
}
