package com.huawei.ascend.service.platform.engine;

import com.huawei.ascend.bus.spi.engine.Checkpointer;
import com.huawei.ascend.bus.spi.engine.DefinitionResolver;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.bus.spi.engine.Orchestrator;
import com.huawei.ascend.engine.runtime.EngineOutcomeChannel;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.engine.runtime.InProcessEnginePort;
import com.huawei.ascend.service.runtime.capability.Capability;
import com.huawei.ascend.service.runtime.capability.CapabilityRegistry;
import com.huawei.ascend.service.runtime.orchestration.A2aEnginePort;
import com.huawei.ascend.service.runtime.orchestration.CompositeDefinitionResolver;
import com.huawei.ascend.service.runtime.orchestration.RpcEnginePort;
import com.huawei.ascend.service.runtime.orchestration.transport.MockEngineChannel;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the dev-posture run-execution spine so {@code POST /v1/runs} actually
 * executes: an in-memory {@link Checkpointer}, the {@link SyncOrchestrator}, the
 * {@link CapabilityRegistry} built from all {@link Capability} beans, and the
 * transport-selected {@link EnginePort} boundary (in_process / internal_rpc / a2a)
 * with its shared {@link DefinitionResolver} + {@link EngineOutcomeChannel}.
 *
 * <p>Gated to {@code dev} posture (matching {@code InMemoryRunRegistry}); the
 * durable, production-posture orchestrator/dispatcher is deferred (ADR-0070, W2 scope).
 * In {@code research}/{@code prod} posture these beans are absent and
 * {@link com.huawei.ascend.service.platform.web.runs.NoOpAsyncRunDispatcher}
 * remains the dispatcher until a durable implementation is provided.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app", name = "posture", havingValue = "dev", matchIfMissing = true)
public class RunExecutionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Checkpointer inMemoryCheckpointer() {
        return new InMemoryCheckpointer();
    }

    @Bean
    @ConditionalOnMissingBean
    public EngineOutcomeChannel engineOutcomeChannel() {
        return new EngineOutcomeChannel();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefinitionResolver definitionResolver(CapabilityRegistry capabilityRegistry) {
        return new CompositeDefinitionResolver(capabilityRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.engine", name = "transport", havingValue = "in_process", matchIfMissing = true)
    public EnginePort inProcessEnginePort(EngineRegistry engineRegistry, DefinitionResolver definitionResolver,
                                          EngineOutcomeChannel engineOutcomeChannel) {
        return new InProcessEnginePort(engineRegistry, definitionResolver, engineOutcomeChannel);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.engine", name = "transport", havingValue = "internal_rpc")
    public EnginePort rpcEnginePort(EngineRegistry engineRegistry, DefinitionResolver definitionResolver,
                                    EngineOutcomeChannel engineOutcomeChannel) {
        return new RpcEnginePort(
                new InProcessEnginePort(engineRegistry, definitionResolver, engineOutcomeChannel),
                new MockEngineChannel());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.engine", name = "transport", havingValue = "a2a")
    public EnginePort a2aEnginePort(EngineRegistry engineRegistry, DefinitionResolver definitionResolver,
                                    EngineOutcomeChannel engineOutcomeChannel) {
        return new A2aEnginePort(
                new InProcessEnginePort(engineRegistry, definitionResolver, engineOutcomeChannel),
                new MockEngineChannel());
    }

    @Bean
    @ConditionalOnMissingBean
    public Orchestrator syncOrchestrator(RunRepository runRepository, Checkpointer checkpointer,
                                         EngineRegistry engineRegistry, EnginePort enginePort,
                                         DefinitionResolver definitionResolver,
                                         EngineOutcomeChannel engineOutcomeChannel) {
        return new SyncOrchestrator(runRepository, checkpointer, engineRegistry, enginePort,
                definitionResolver, engineOutcomeChannel);
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityRegistry capabilityRegistry(List<Capability> capabilities) {
        return new CapabilityRegistry(capabilities);
    }
}
