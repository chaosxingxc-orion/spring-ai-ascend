package com.huawei.ascend.service.runtime.orchestration;

import com.huawei.ascend.bus.spi.engine.Checkpointer;
import com.huawei.ascend.bus.spi.engine.DefinitionResolver;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.engine.runtime.EngineOutcomeChannel;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.engine.runtime.InProcessEnginePort;
import com.huawei.ascend.service.runtime.capability.CapabilityRegistry;
import com.huawei.ascend.service.runtime.orchestration.inmemory.SyncOrchestrator;
import com.huawei.ascend.service.runtime.runs.spi.RunRepository;

import java.time.Duration;
import java.util.List;

/** Test convenience for wiring SyncOrchestrator with a real in-process EnginePort. */
public final class TestEnginePorts {
    private TestEnginePorts() {}

    public static SyncOrchestrator inProcessOrchestrator(RunRepository runs, Checkpointer cp, EngineRegistry reg) {
        EngineOutcomeChannel outcomes = new EngineOutcomeChannel();
        DefinitionResolver resolver = new CompositeDefinitionResolver(new CapabilityRegistry(List.of()));
        EnginePort port = new InProcessEnginePort(reg, resolver, outcomes);
        return new SyncOrchestrator(runs, cp, reg, port, resolver, outcomes);
    }

    public static SyncOrchestrator inProcessOrchestrator(RunRepository runs, Checkpointer cp, EngineRegistry reg, Duration s2cTimeout) {
        EngineOutcomeChannel outcomes = new EngineOutcomeChannel();
        DefinitionResolver resolver = new CompositeDefinitionResolver(new CapabilityRegistry(List.of()));
        EnginePort port = new InProcessEnginePort(reg, resolver, outcomes);
        return new SyncOrchestrator(runs, cp, reg, port, resolver, outcomes, s2cTimeout);
    }
}
