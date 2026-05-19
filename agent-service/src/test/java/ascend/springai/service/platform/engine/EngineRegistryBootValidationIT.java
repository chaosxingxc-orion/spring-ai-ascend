package ascend.springai.service.platform.engine;

import ascend.springai.service.runtime.engine.EngineRegistry;
import ascend.springai.service.runtime.orchestration.inmemory.SequentialGraphExecutor;
import ascend.springai.engine.spi.ExecutorAdapter;
import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.engine.spi.GraphExecutor;
import ascend.springai.engine.orchestration.spi.RunContext;
import ascend.springai.engine.orchestration.spi.SuspendSignal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2.x Phase 5 R2 pilot - boot-time self-validation of the engine envelope
 * schema (ADR-0076, extends ADR-0071 + ADR-0072).
 *
 * <p>Three cases:
 * <ul>
 *   <li>{@code boot_succeeds_when_registered_adapters_match_known_engines} -
 *   the default {@link EngineAutoConfiguration} registers the two reference
 *   adapters and the registry self-validates GREEN.</li>
 *   <li>{@code boot_fails_when_known_engine_has_no_adapter} - dropping the
 *   {@code AgentLoopExecutor} bean leaves {@code agent-loop} unimplemented;
 *   the context bootstrap MUST fail with
 *   {@code missing adapters for known_engines=[agent-loop]}.</li>
 *   <li>{@code boot_fails_when_adapter_not_in_known_engines} - registering an
 *   extra adapter advertising {@code engineType()="frankenstein"} that is not
 *   listed in {@code docs/contracts/engine-envelope.v1.yaml} MUST fail with
 *   {@code unknown registered adapters=[frankenstein]}.</li>
 * </ul>
 *
 * <p>Each case drives the Spring context programmatically via
 * {@link SpringApplicationBuilder} - this keeps the test off the Postgres /
 * Flyway / OAuth2 stacks the full {@code PlatformApplication} would pull in,
 * and lets us assert hard-fail bootstrap behaviour. CLAUDE.md Rule D-4: this
 * is an integration test that exercises real Spring bean creation; there
 * are no mocks on the subsystem under test.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E84 (post-review fix
 * plan F / P1-2: prior Javadoc cited #E81 which is the S2C-callback gate
 * row, not this IT).
 */
class EngineRegistryBootValidationIT {

    /**
     * Surefire runs the test forked from the module dir (post-Phase-C:
     * agent-service/; pre-Phase-C: agent-platform/ per ADR-0078), but
     * the default schema path docs/contracts/engine-envelope.v1.yaml lives at
     * the repo root. Resolve the absolute path here so the test is invariant
     * to module-vs-root cwd.
     */
    private static String schemaPathAbsolute() {
        java.nio.file.Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        java.nio.file.Path candidate = cwd.resolve("docs/contracts/engine-envelope.v1.yaml");
        if (java.nio.file.Files.isRegularFile(candidate)) {
            return candidate.toString();
        }
        // Module cwd - climb one level to the repo root.
        java.nio.file.Path parent = cwd.getParent();
        if (parent != null) {
            java.nio.file.Path rooted = parent.resolve("docs/contracts/engine-envelope.v1.yaml");
            if (java.nio.file.Files.isRegularFile(rooted)) {
                return rooted.toString();
            }
        }
        throw new IllegalStateException(
                "Could not locate engine-envelope.v1.yaml relative to cwd=" + cwd);
    }

    private static ConfigurableApplicationContext launch(Class<?> primarySource) {
        return new SpringApplicationBuilder(primarySource)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "app.engine.envelope-schema-path=" + schemaPathAbsolute())
                .run();
    }

    @Test
    void boot_succeeds_when_registered_adapters_match_known_engines() {
        try (ConfigurableApplicationContext ctx = launch(DefaultBootConfig.class)) {
            EngineRegistry registry = ctx.getBean(EngineRegistry.class);
            assertThat(registry).isNotNull();
            assertThat(registry.registeredEngineTypes())
                    .containsExactlyInAnyOrder("graph", "agent-loop");
        }
    }

    @Test
    void boot_fails_when_known_engine_has_no_adapter() {
        assertThatThrownBy(() -> launch(MissingAdapterBootConfig.class))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing adapters for known_engines")
                .hasMessageContaining("agent-loop");
    }

    @Test
    void boot_fails_when_adapter_not_in_known_engines() {
        assertThatThrownBy(() -> launch(FrankensteinBootConfig.class))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown registered adapters")
                .hasMessageContaining("frankenstein");
    }

    // Case 1 - default platform wiring: pulls in both reference adapters via
    // EngineAutoConfiguration's @ConditionalOnMissingBean defaults.
    @SpringBootConfiguration
    @Import(EngineAutoConfiguration.class)
    static class DefaultBootConfig {
    }

    // Case 2 - only GraphExecutor is registered; AgentLoopExecutor is absent so
    // EngineRegistry.validateAgainstSchema must detect the missing adapter.
    @SpringBootConfiguration
    static class MissingAdapterBootConfig {

        @Bean
        public GraphExecutor sequentialGraphExecutor() {
            return new SequentialGraphExecutor();
        }

        @Bean
        public EngineRegistry engineRegistry(List<ExecutorAdapter> adapters,
                                             @org.springframework.beans.factory.annotation.Value(
                                                     "${app.engine.envelope-schema-path}") String schemaPath) {
            EngineRegistry registry = new EngineRegistry();
            adapters.forEach(registry::register);
            registry.validateAgainstSchema(schemaPath);
            return registry;
        }
    }

    // Case 3 - default reference adapters PLUS a "frankenstein" adapter whose
    // engineType is NOT in known_engines. validateAgainstSchema must flag the
    // unknown registered adapter and abort boot. We hand-wire (no Import of
    // EngineAutoConfiguration) to control the adapter set: the SequentialGraph
    // reference adapter keeps the GraphDefinition payload slot occupied, the
    // FrankensteinAdapter declares engineType=frankenstein bound to the still
    // free AgentLoopDefinition payload slot, and no AgentLoopExecutor bean is
    // registered. The validateAgainstSchema call thus surfaces both an unknown
    // registered adapter (frankenstein) and a missing adapter (agent-loop);
    // we assert on the unknown clause since that is the case-3 invariant.
    @SpringBootConfiguration
    static class FrankensteinBootConfig {

        @Bean
        public GraphExecutor sequentialGraphExecutor() {
            return new SequentialGraphExecutor();
        }

        @Bean
        public ExecutorAdapter frankensteinAdapter() {
            return new FrankensteinAdapter();
        }

        @Bean
        public EngineRegistry engineRegistry(List<ExecutorAdapter> adapters,
                                             @org.springframework.beans.factory.annotation.Value(
                                                     "${app.engine.envelope-schema-path}") String schemaPath) {
            EngineRegistry registry = new EngineRegistry();
            adapters.forEach(registry::register);
            registry.validateAgainstSchema(schemaPath);
            return registry;
        }
    }

    /**
     * Adapter whose engineType is intentionally absent from known_engines so
     * validateAgainstSchema detects an unknown registered engine. Bound to the
     * AgentLoopDefinition payload slot so it does not collide with
     * SequentialGraphExecutor's GraphDefinition slot during register().
     */
    static final class FrankensteinAdapter implements ExecutorAdapter {

        @Override
        public String engineType() {
            return "frankenstein";
        }

        @Override
        public Class<? extends ExecutorDefinition> payloadType() {
            return ExecutorDefinition.AgentLoopDefinition.class;
        }

        @Override
        public Object execute(RunContext ctx, ExecutorDefinition def, Object payload) throws SuspendSignal {
            return null;
        }
    }
}
