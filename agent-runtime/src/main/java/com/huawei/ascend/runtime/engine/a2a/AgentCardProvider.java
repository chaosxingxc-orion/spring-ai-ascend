package com.huawei.ascend.runtime.engine.a2a;

import org.a2aproject.sdk.spec.AgentCard;

/**
 * Provides the A2A Agent Card for one runtime-hosted business Agent.
 *
 * <p>This is separated from
 * {@link com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler}: executing
 * an Agent and describing its public A2A metadata are two different
 * capabilities. If a bean of this type is registered, its card replaces the
 * auto-generated one; a concrete business handler can also implement both
 * interfaces when that is the simplest shape. The Agent Card is A2A protocol
 * metadata by nature, so this supplier lives in the protocol bridge package
 * rather than the neutral SPI.
 */
public interface AgentCardProvider {

    /** Returns the A2A Agent Card exposed by this runtime instance. */
    AgentCard agentCard();
}
