package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.engine.runtime.EngineOutcomeChannel;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.engine.runtime.InProcessEnginePort;
import com.huawei.ascend.bus.spi.engine.DefinitionResolver;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.service.runtime.orchestration.A2aEnginePort;
import com.huawei.ascend.service.runtime.orchestration.transport.MockEngineChannel;

/**
 * A2A federation mock transport conformance — proves the mock-functional
 * {@link A2aEnginePort} (wrap request in a federation envelope -> round-trip ->
 * dispatch to co-located engine -> re-serialize events) passes the identical
 * {@link EnginePortConformanceTck} battery as the in-process port.
 */
final class A2aEnginePortConformanceTest extends EnginePortConformanceTck {

    @Override
    protected EnginePort portUnderTest(EngineRegistry registry, DefinitionResolver resolver,
                                       EngineOutcomeChannel outcomes) {
        return new A2aEnginePort(new InProcessEnginePort(registry, resolver, outcomes),
                new MockEngineChannel());
    }
}
