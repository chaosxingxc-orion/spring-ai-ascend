package com.huawei.ascend.bus.spi.s2c;

import java.util.Map;

/**
 * Reflection envelope router SPI.
 *
 * <p>Pushes reflection updates from the cloud Slow Track judge
 * ({@code com.huawei.ascend.evolve.online.spi.SlowTrackJudge}) → edge
 * Fast Track via the agent-bus S2C transport.
 *
 * <p>This is the cloud-to-edge half of the Heaven-Earth Coordination
 * cell (Mode B + Online). The edge consumer is
 * {@code com.huawei.ascend.service.session.ReflectionPatchHandler}.
 *
 * <p>Wire contract: {@code docs/contracts/reflection-envelope.v1.yaml}.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + own
 * siblings.
 */
public interface ReflectionEnvelopeRouter {

    /**
     * Route a reflection envelope to the target Session.
     *
     * @param reflectionEnvelope envelope shape mirroring the v1 YAML contract.
     */
    void route(Map<String, Object> reflectionEnvelope);
}
