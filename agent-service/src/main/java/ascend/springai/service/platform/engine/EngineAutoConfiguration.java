package ascend.springai.service.platform.engine;

import ascend.springai.engine.runtime.EngineRegistry;
import ascend.springai.service.runtime.orchestration.inmemory.IterativeAgentLoopExecutor;
import ascend.springai.service.runtime.orchestration.inmemory.SequentialGraphExecutor;
import ascend.springai.engine.spi.AgentLoopExecutor;
import ascend.springai.engine.spi.ExecutorAdapter;
import ascend.springai.engine.spi.GraphExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the W2.x Phase 5 R2 pilot - runtime self-validation of the engine
 * envelope schema (ADR-0076, extends ADR-0071 and ADR-0072).
 *
 * <p>Boot order is single-pass: every {@link ExecutorAdapter} bean discovered
 * in the Spring context is registered with {@link EngineRegistry}, then
 * {@link EngineRegistry#validateAgainstSchema()} runs once. If the registered
 * set does not match {@code known_engines} in
 * {@code docs/contracts/engine-envelope.v1.yaml}, the bean factory raises
 * {@link IllegalStateException} and the application fails to start. This is
 * the Rule 9 ship-gate posture - misconfiguration cannot reach production.
 *
 * <p>Two reference executors are registered by default: the W0 reference
 * {@link SequentialGraphExecutor} (engineType=graph) and
 * {@link IterativeAgentLoopExecutor} (engineType=agent-loop). Both are
 * conditional on missing beans so an integrator can supply alternative
 * implementations without removing the default wiring.
 *
 * <p>The schema path is configurable via {@code app.engine.envelope-schema-path}.
 * The default is correct for two launch modes:
 * <ul>
 *   <li>Unit-test / reactor launches from the repo root, where the YAML is
 *       reachable via the literal filesystem path {@code docs/contracts/...}.</li>
 *   <li>Packaged-jar deployments, where the {@code agent-service} jar's classpath
 *       (post-Phase-C / ADR-0078 consolidation; pre-Phase-C this was the
 *       {@code agent-platform} jar) resource at
 *       {@code /docs/contracts/engine-envelope.v1.yaml} resolves because
 *       {@code agent-service/pom.xml} (formerly {@code agent-platform/pom.xml})
 *       declares a {@code <resources>} rule copying canonical contract YAMLs
 *       from {@code ../docs/contracts} into
 *       {@code target/classes/docs/contracts/} at package time (post-review
 *       fix plan E / P0-4).</li>
 * </ul>
 * Boot validation runs synchronously inside the {@link #engineRegistry} bean
 * factory; both filesystem and classpath fallbacks are exercised by
 * {@code EngineRegistry.readYaml()}. An integrator overriding the property
 * MUST ensure the override resolves under the same dual-mode contract.
 *
 * <p>Authority: ADR-0076; Layer-0 principle P-M.
 */
@Configuration(proxyBeanMethods = false)
public class EngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GraphExecutor.class)
    public GraphExecutor sequentialGraphExecutor() {
        return new SequentialGraphExecutor();
    }

    @Bean
    @ConditionalOnMissingBean(AgentLoopExecutor.class)
    public AgentLoopExecutor iterativeAgentLoopExecutor() {
        return new IterativeAgentLoopExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineRegistry engineRegistry(
            List<ExecutorAdapter> adapters,
            @Value("${app.engine.envelope-schema-path:docs/contracts/engine-envelope.v1.yaml}")
                    String schemaPath) {
        EngineRegistry registry = new EngineRegistry();
        adapters.forEach(registry::register);
        // Phase 5 R2 pilot - fail fast at boot if the registered adapter set
        // does not match docs/contracts/engine-envelope.v1.yaml known_engines.
        registry.validateAgainstSchema(schemaPath);
        return registry;
    }
}
