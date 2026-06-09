package com.huawei.ascend.runtime.engine.spi;

import org.a2aproject.sdk.spec.AgentCard;

/**
 * Provides the A2A Agent Card for one runtime-hosted business Agent.
 *
 * <p>This is separated from {@link AgentRuntimeHandler}: executing an Agent and
 * describing its public A2A metadata are two different capabilities. A runtime
 * can provide this as a separate Bean, or a concrete business handler can
 * implement both interfaces when that is the simplest shape.
 */
public interface AgentCardProvider {

    /** Returns the A2A Agent Card exposed by this runtime instance. */
    AgentCard agentCard();
}
