package com.huawei.ascend.service.runtime.orchestration.inmemory;

import com.huawei.ascend.engine.runtime.EngineOutcomeChannel;
import com.huawei.ascend.engine.runtime.EngineRegistry;
import com.huawei.ascend.engine.runtime.InProcessEnginePort;
import com.huawei.ascend.bus.spi.engine.DefinitionResolver;
import com.huawei.ascend.bus.spi.engine.EnginePort;
import com.huawei.ascend.service.runtime.orchestration.RpcEnginePort;
import com.huawei.ascend.service.runtime.orchestration.transport.MockEngineChannel;

/**
 * Internal-RPC mock transport conformance — proves the mock-functional
 * {@link RpcEnginePort} (serialize request -> dispatch to co-located engine ->
 * re-serialize events) passes the identical {@link EnginePortConformanceTck} battery
 * as the in-process port, so the boundary is transport-agnostic.
 */
final class RpcEnginePortConformanceTest extends EnginePortConformanceTck {

    @Override
    protected EnginePort portUnderTest(EngineRegistry registry, DefinitionResolver resolver,
                                       EngineOutcomeChannel outcomes) {
        return new RpcEnginePort(new InProcessEnginePort(registry, resolver, outcomes),
                new MockEngineChannel());
    }
}
