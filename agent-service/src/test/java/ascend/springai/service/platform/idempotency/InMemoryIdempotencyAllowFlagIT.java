package ascend.springai.service.platform.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the posture × {@code allow-in-memory} matrix from ADR-0057 §6.
 * Boots three minimal Spring contexts (no web, no DataSource) with different
 * property combinations and asserts which {@link IdempotencyStore} bean — if
 * any — is wired.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E22.
 */
class InMemoryIdempotencyAllowFlagIT {

    @Test
    void dev_with_allow_in_memory_true_wires_in_memory_store() {
        try (ConfigurableApplicationContext ctx = boot(
                "app.posture=dev",
                "app.idempotency.allow-in-memory=true")) {
            IdempotencyStore store = ctx.getBean(IdempotencyStore.class);
            assertThat(store).isInstanceOf(InMemoryIdempotencyStore.class);
        }
    }

    @Test
    void dev_with_allow_in_memory_false_does_not_register_any_store() {
        try (ConfigurableApplicationContext ctx = boot(
                "app.posture=dev",
                "app.idempotency.allow-in-memory=false")) {
            assertThatThrownBy(() -> ctx.getBean(IdempotencyStore.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }

    @Test
    void research_with_allow_in_memory_true_must_not_wire_in_memory_store() {
        // PostureBootGuard (Phase F) will additionally abort startup for this
        // combination once that bean lands. Until then the @ConditionalOnExpression
        // alone must already deny the in-memory bean — covered here.
        try (ConfigurableApplicationContext ctx = boot(
                "app.posture=research",
                "app.idempotency.allow-in-memory=true")) {
            assertThatThrownBy(() -> ctx.getBean(IdempotencyStore.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }

    private static ConfigurableApplicationContext boot(String... properties) {
        SpringApplication app = new SpringApplication(MinimalConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        return app.run(toCliArgs(properties));
    }

    private static String[] toCliArgs(String[] properties) {
        String[] args = new String[properties.length];
        for (int i = 0; i < properties.length; i++) {
            args[i] = "--" + properties[i];
        }
        return args;
    }

    /**
     * Minimal configuration scope: brings up {@link IdempotencyProperties}
     * binding and {@link IdempotencyStoreAutoConfiguration} only. No
     * component scan (avoids pulling in IdempotencyFilterAutoConfiguration,
     * security beans, etc.); no DataSource (the {@link JdbcIdempotencyStore}
     * branch stays inert and is exercised by {@link IdempotencyStorePostgresIT}).
     */
    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(IdempotencyProperties.class)
    @org.springframework.context.annotation.Import(IdempotencyStoreAutoConfiguration.class)
    static class MinimalConfig {
    }
}
