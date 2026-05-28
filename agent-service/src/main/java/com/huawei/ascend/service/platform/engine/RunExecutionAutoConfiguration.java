package com.huawei.ascend.service.platform.engine;

import com.huawei.ascend.engine.orchestration.spi.Checkpointer;
import com.huawei.ascend.engine.orchestration.spi.Orchestrator;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.service.runtime.capability.Capability;
import com.huawei.ascend.service.runtime.capability.CapabilityRegistry;
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
 * executes: an in-memory {@link Checkpointer}, the {@link SyncOrchestrator},
 * and the {@link CapabilityRegistry} built from all {@link Capability} beans.
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
    public Orchestrator syncOrchestrator(RunRepository runRepository,
                                         Checkpointer checkpointer,
                                         EngineRegistry engineRegistry) {
        return new SyncOrchestrator(runRepository, checkpointer, engineRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityRegistry capabilityRegistry(List<Capability> capabilities) {
        return new CapabilityRegistry(capabilities);
    }
}
