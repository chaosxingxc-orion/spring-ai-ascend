package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.engine.runtime.EngineOutcomeChannel;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.engine.runtime.InProcessEnginePort;
import com.huawei.ascend.bus.spi.engine.DefinitionResolver;
import com.huawei.ascend.bus.spi.engine.EnginePort;

/**
 * In-process transport conformance — the {@link EnginePort} realization used when
 * the Service and engine share a JVM (co-deployed, or SDK-embedded). This is the
 * golden reference every other transport must reproduce.
 */
final class InProcessEnginePortConformanceTest extends EnginePortConformanceTck {

    @Override
    protected EnginePort portUnderTest(EngineRegistry registry, DefinitionResolver resolver,
                                       EngineOutcomeChannel outcomes) {
        return new InProcessEnginePort(registry, resolver, outcomes);
    }
}
