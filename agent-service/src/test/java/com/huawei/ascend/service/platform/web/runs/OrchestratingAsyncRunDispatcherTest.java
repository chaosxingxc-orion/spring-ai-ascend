package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.bus.spi.engine.Checkpointer;
import com.huawei.ascend.bus.spi.engine.ExecutorDefinition;
import com.huawei.ascend.bus.spi.engine.Orchestrator;
import com.huawei.ascend.bus.spi.engine.RunMode;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.service.runtime.capability.Capability;
import com.huawei.ascend.service.runtime.capability.CapabilityRegistry;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryCheckpointer;
import com.huawei.ascend.service.runtime.orchestration.inmemory.InMemoryRunRegistry;
import com.huawei.ascend.engine.exec.SequentialGraphExecutor;
import com.huawei.ascend.service.runtime.orchestration.TestEnginePorts;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import com.huawei.ascend.service.runtime.runs.Run;
import com.huawei.ascend.service.runtime.runs.RunStatus;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level proof of the dev-posture execution spine without HTTP / Postgres /
 * Docker: a {@link Run} dispatched through {@link OrchestratingAsyncRunDispatcher}
 * (backed by {@link SyncOrchestrator}) reaches a terminal state. The
 * Testcontainers {@code RunExecutionIT} proves the same path over the full HTTP
 * surface in CI.
 */
class OrchestratingAsyncRunDispatcherTest {

    private static final String TENANT = UUID.randomUUID().toString();

    private OrchestratingAsyncRunDispatcher dispatcher(RunRepository runs, Capability... capabilities) {
        Checkpointer checkpointer = new InMemoryCheckpointer();
        EngineRegistry engineRegistry = new EngineRegistry().register(new SequentialGraphExecutor());
        Orchestrator orchestrator = TestEnginePorts.inProcessOrchestrator(runs, checkpointer, engineRegistry);
        CapabilityRegistry registry = new CapabilityRegistry(List.of(capabilities));
        return new OrchestratingAsyncRunDispatcher(runs, orchestrator, registry);
    }

    private Run pendingRun(String capabilityName) {
        Instant now = Instant.now();
        return new Run(UUID.randomUUID(), TENANT, capabilityName, RunStatus.PENDING,
                RunMode.GRAPH, now, now, null, null, null, null, null, null, null);
    }

    private static Capability singleNodeGraph(String name) {
        return new Capability() {
            @Override
            public String capabilityName() {
                return name;
            }

            @Override
            public ExecutorDefinition definition() {
                ExecutorDefinition.NodeFunction node =
                        (ctx, payload) -> Map.of("executed", true);
                return new ExecutorDefinition.GraphDefinition(Map.of("run", node), Map.of(), "run");
            }
        };
    }

    @Test
    void knownCapabilityDrivesRunToSucceeded() {
        RunRepository runs = new InMemoryRunRegistry();
        Capability capability = singleNodeGraph("demo");
        Run run = runs.save(pendingRun("demo"));

        dispatcher(runs, capability).dispatch(run);

        Run finished = runs.findById(run.runId()).orElseThrow();
        assertThat(finished.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(finished.finishedAt()).isNotNull();
    }

    @Test
    void unknownCapabilityDrivesRunToFailed() {
        RunRepository runs = new InMemoryRunRegistry();
        Run run = runs.save(pendingRun("missing"));

        dispatcher(runs).dispatch(run);

        Run finished = runs.findById(run.runId()).orElseThrow();
        assertThat(finished.status()).isEqualTo(RunStatus.FAILED);
    }
}
