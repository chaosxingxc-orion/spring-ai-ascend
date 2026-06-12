package com.huawei.ascend.runtime.engine.a2a;

import org.a2aproject.sdk.spec.AgentCard;

/**
 * Provides the A2A Agent Card for one runtime-hosted business Agent.
 *
 * <p>This is a user-overridable hook — not a core SPI. If a bean of this
 * type is registered, its card replaces the auto-generated one. A concrete
 * handler may also implement this interface directly.
 */
public interface AgentCardProvider {

    /** Returns the A2A Agent Card exposed by this runtime instance. */
    AgentCard agentCard();
}
